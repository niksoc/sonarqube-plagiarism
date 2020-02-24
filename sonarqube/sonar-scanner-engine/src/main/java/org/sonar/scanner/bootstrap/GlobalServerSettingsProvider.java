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
package org.sonar.scanner.bootstrap;

import java.util.Map;
import java.util.Optional;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.CoreProperties;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.scanner.repository.settings.GlobalSettingsLoader;

public class GlobalServerSettingsProvider extends ProviderAdapter {

  private static final Logger LOG = Loggers.get(GlobalServerSettingsProvider.class);

  private GlobalServerSettings singleton;

  public GlobalServerSettings provide(GlobalSettingsLoader loader) {
    if (singleton == null) {
      Map<String, String> serverSideSettings = loader.loadGlobalSettings();
      singleton = new GlobalServerSettings(serverSideSettings);
      Optional.ofNullable(serverSideSettings.get(CoreProperties.SERVER_ID)).ifPresent(v -> LOG.info("Server id: {}", v));
    }
    return singleton;
  }
}
