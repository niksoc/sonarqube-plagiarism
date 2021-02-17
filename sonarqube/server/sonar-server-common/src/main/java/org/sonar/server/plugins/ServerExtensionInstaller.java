/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.plugins;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.sonar.api.ExtensionProvider;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;
import org.sonar.api.config.Configuration;
import org.sonar.api.internal.PluginContextImpl;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.MessageException;
import org.sonar.core.platform.ComponentContainer;
import org.sonar.core.platform.PluginInfo;
import org.sonar.core.platform.PluginRepository;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.sonar.core.extension.ExtensionProviderSupport.isExtensionProvider;

/**
 * Loads the plugins server extensions and injects them to DI container
 */
public abstract class ServerExtensionInstaller {

  private static final Set<String> NO_MORE_COMPATIBLE_PLUGINS = ImmutableSet.of("authgithub", "authgitlab", "authsaml", "ldap");

  private final SonarRuntime sonarRuntime;
  private final PluginRepository pluginRepository;
  private final Set<Class<? extends Annotation>> supportedAnnotationTypes;

  protected ServerExtensionInstaller(SonarRuntime sonarRuntime, PluginRepository pluginRepository,
    Collection<Class<? extends Annotation>> supportedAnnotationTypes) {
    requireNonNull(supportedAnnotationTypes, "At least one supported annotation type must be specified");
    this.sonarRuntime = sonarRuntime;
    this.pluginRepository = pluginRepository;
    this.supportedAnnotationTypes = ImmutableSet.copyOf(supportedAnnotationTypes);
  }

  public void installExtensions(ComponentContainer container) {
    failWhenNoMoreCompatiblePlugins();
    ListMultimap<PluginInfo, Object> installedExtensionsByPlugin = ArrayListMultimap.create();
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      try {
        String pluginKey = pluginInfo.getKey();
        Plugin plugin = pluginRepository.getPluginInstance(pluginKey);
        container.addExtension(pluginInfo, plugin);

        Plugin.Context context = new PluginContextImpl.Builder()
          .setSonarRuntime(sonarRuntime)
          .setBootConfiguration(container.getComponentByType(Configuration.class))
          .build();
        plugin.define(context);
        for (Object extension : context.getExtensions()) {
          if (installExtension(container, pluginInfo, extension, true) != null) {
            installedExtensionsByPlugin.put(pluginInfo, extension);
          } else {
            container.declareExtension(pluginInfo, extension);
          }
        }
      } catch (Throwable e) {
        // catch Throwable because we want to catch Error too (IncompatibleClassChangeError, ...)
        throw new IllegalStateException(format("Fail to load plugin %s [%s]", pluginInfo.getName(), pluginInfo.getKey()), e);
      }
    }
    for (Map.Entry<PluginInfo, Object> entry : installedExtensionsByPlugin.entries()) {
      PluginInfo pluginInfo = entry.getKey();
      try {
        Object extension = entry.getValue();
        if (isExtensionProvider(extension)) {
          ExtensionProvider provider = (ExtensionProvider) container.getComponentByKey(extension);
          installProvider(container, pluginInfo, provider);
        }
      } catch (Throwable e) {
        // catch Throwable because we want to catch Error too (IncompatibleClassChangeError, ...)
        throw new IllegalStateException(format("Fail to load plugin %s [%s]", pluginInfo.getName(), pluginInfo.getKey()), e);
      }
    }
  }

  private void failWhenNoMoreCompatiblePlugins() {
    Set<String> noMoreCompatiblePluginNames = pluginRepository.getPluginInfos()
      .stream()
      .filter(pluginInfo -> NO_MORE_COMPATIBLE_PLUGINS.contains(pluginInfo.getKey()))
      .map(PluginInfo::getName)
      .collect(Collectors.toCollection(TreeSet::new));
    if (!noMoreCompatiblePluginNames.isEmpty()) {
      throw MessageException.of(format("Plugins '%s' are no longer compatible with this version of SonarQube. Refer to https://docs.sonarqube.org/latest/instance-administration/plugin-version-matrix/", String.join(", ", noMoreCompatiblePluginNames)));
    }
  }

  private void installProvider(ComponentContainer container, PluginInfo pluginInfo, ExtensionProvider provider) {
    Object obj = provider.provide();
    if (obj != null) {
      if (obj instanceof Iterable) {
        for (Object ext : (Iterable) obj) {
          installExtension(container, pluginInfo, ext, false);
        }
      } else {
        installExtension(container, pluginInfo, obj, false);
      }
    }
  }

  private Object installExtension(ComponentContainer container, PluginInfo pluginInfo, Object extension, boolean acceptProvider) {
    for (Class<? extends Annotation> supportedAnnotationType : supportedAnnotationTypes) {
      if (AnnotationUtils.getAnnotation(extension, supportedAnnotationType) != null) {
        if (!acceptProvider && isExtensionProvider(extension)) {
          throw new IllegalStateException("ExtensionProvider can not include providers itself: " + extension);
        }
        container.addExtension(pluginInfo, extension);
        return extension;
      }
    }
    return null;
  }

}
