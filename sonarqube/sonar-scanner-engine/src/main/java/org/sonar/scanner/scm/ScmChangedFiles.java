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
package org.sonar.scanner.scm;

import java.nio.file.Path;
import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
public class ScmChangedFiles {
  @Nullable
  private final Collection<Path> fileCollection;

  public ScmChangedFiles(@Nullable Collection<Path> changedFiles) {
    this.fileCollection = changedFiles;
  }

  public boolean isChanged(Path file) {
    if (!isValid()) {
      throw new IllegalStateException("Scm didn't provide valid data");
    }

    return fileCollection.contains(file);
  }

  public boolean isValid() {
    return fileCollection != null;
  }

  @CheckForNull
  Collection<Path> get() {
    return fileCollection;
  }
}
