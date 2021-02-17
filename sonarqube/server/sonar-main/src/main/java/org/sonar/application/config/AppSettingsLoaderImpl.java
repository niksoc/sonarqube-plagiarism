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
package org.sonar.application.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;
import org.sonar.core.extension.ServiceLoaderWrapper;
import org.sonar.process.ConfigurationUtils;
import org.sonar.process.NetworkUtilsImpl;
import org.sonar.process.ProcessProperties;
import org.sonar.process.Props;
import org.sonar.process.System2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;
import static org.sonar.core.util.SettingFormatter.fromJavaPropertyToEnvVariable;
import static org.sonar.process.ProcessProperties.Property.PATH_HOME;

public class AppSettingsLoaderImpl implements AppSettingsLoader {

  private final System2 system;
  private final File homeDir;
  private final String[] cliArguments;
  private final Consumer<Props>[] consumers;
  private final ServiceLoaderWrapper serviceLoaderWrapper;

  public AppSettingsLoaderImpl(System2 system, String[] cliArguments, ServiceLoaderWrapper serviceLoaderWrapper) {
    this(system, cliArguments, detectHomeDir(), serviceLoaderWrapper, new FileSystemSettings(), new JdbcSettings(),
      new ClusterSettings(NetworkUtilsImpl.INSTANCE));
  }

  @SafeVarargs
  AppSettingsLoaderImpl(System2 system, String[] cliArguments, File homeDir, ServiceLoaderWrapper serviceLoaderWrapper, Consumer<Props>... consumers) {
    this.system = system;
    this.cliArguments = cliArguments;
    this.homeDir = homeDir;
    this.serviceLoaderWrapper = serviceLoaderWrapper;
    this.consumers = consumers;
  }

  File getHomeDir() {
    return homeDir;
  }

  @Override
  public AppSettings load() {
    Properties p = loadPropertiesFile(homeDir);
    fetchSettingsFromEnvironment(system, p);
    p.putAll(CommandLineParser.parseArguments(cliArguments));
    p.setProperty(PATH_HOME.getKey(), homeDir.getAbsolutePath());
    p = ConfigurationUtils.interpolateVariables(p, system.getenv());

    // the difference between Properties and Props is that the latter
    // supports decryption of values, so it must be used when values
    // are accessed
    Props props = new Props(p);
    new ProcessProperties(serviceLoaderWrapper).completeDefaults(props);
    Arrays.stream(consumers).forEach(c -> c.accept(props));

    return new AppSettingsImpl(props);
  }

  private static void fetchSettingsFromEnvironment(System2 system, Properties properties) {
    Set<String> possibleSettings = Arrays.stream(ProcessProperties.Property.values()).map(ProcessProperties.Property::getKey)
      .collect(Collectors.toSet());
    possibleSettings.addAll(properties.stringPropertyNames());
    possibleSettings.forEach(key -> {
      String environmentVarName = fromJavaPropertyToEnvVariable(key);
      Optional<String> envVarValue = ofNullable(system.getenv(environmentVarName));
      envVarValue.ifPresent(value -> properties.put(key, value));
    });
  }

  private static File detectHomeDir() {
    try {
      File appJar = new File(Class.forName("org.sonar.application.App").getProtectionDomain().getCodeSource().getLocation().toURI());
      return appJar.getParentFile().getParentFile();
    } catch (URISyntaxException | ClassNotFoundException e) {
      throw new IllegalStateException("Cannot detect path of main jar file", e);
    }
  }

  /**
   * Loads the configuration file ${homeDir}/conf/sonar.properties.
   * An empty {@link Properties} is returned if the file does not exist.
   */
  private static Properties loadPropertiesFile(File homeDir) {
    Properties p = new Properties();
    File propsFile = new File(homeDir, "conf/sonar.properties");
    if (propsFile.exists()) {
      try (Reader reader = new InputStreamReader(new FileInputStream(propsFile), UTF_8)) {
        p.load(reader);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot open file " + propsFile, e);
      }
    } else {
      LoggerFactory.getLogger(AppSettingsLoaderImpl.class).warn("Configuration file not found: {}", propsFile);
    }
    return p;
  }
}
