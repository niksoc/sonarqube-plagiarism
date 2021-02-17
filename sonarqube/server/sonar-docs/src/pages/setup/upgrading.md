---
title: Upgrade the Server
url: /setup/upgrading/
---

<!-- sonarqube -->
Upgrading across multiple, non-LTS versions is handled automatically. However, if you have an LTS version in your migration path, you must first migrate to this LTS and then migrate to your target version.

Example 1 : 5.1 -> 7.0, migration path is 5.1 -> 5.6.7 LTS -> 6.7.x LTS -> 7.0
Example 2 : 6.2 -> 6.7, migration path is 6.2 -> 6.7.x LTS (where x is the latest patch available for 6.7 - you don't need to install all the intermediary patches, just take the latest)

## Upgrade Guide

This is a generic upgrade guide. Carefully read the [Release Upgrade Notes](/setup/upgrade-notes/) of your target version and of any intermediate version(s).

[[warning]]
| Before you start, back up your SonarQube Database. Upgrade problems are rare, but you'll want the backup if anything does happen.

### Upgrading from the ZIP file

1. Download and unzip the SonarQube distribution of your edition in a fresh directory, let's say `$NEW_SONARQUBE_HOME`
2. Manually install the non-default plugins that are compatible with your version of SonarQube. Use the [Compatibility Matrix](https://docs.sonarqube.org/display/PLUG/Plugin+Version+Matrix) to ensure that the versions you install are compatible with your server version. Note that the most recent versions of all SonarSource code analyzers available in your edition are installed by default. Simply copying plugins from the old server to the new is not recommended; incompatible or duplicate plugins could cause startup errors.
3. Update the contents of `sonar.properties` and `wrapper.conf` files (in `$NEW_SONARQUBE_HOME/conf`) with the settings of the related files in the `$OLD_SONARQUBE_HOME/conf` directory (web server URL, database, ldap settings, etc.). Do not copy-paste the old files.
If you are using the Oracle DB, copy its JDBC driver into `$NEW_SONARQUBE_HOME/extensions/jdbc-driver/oracle`
4. Stop your old SonarQube Server
5. Start your new SonarQube Server
6. Browse to `http://yourSonarQubeServerURL/setup` and follow the setup instructions
7. Reanalyze your projects to get fresh data

### Upgrading from the Docker image

### To 8.2+

To upgrade to SonarQube 8.2+:

1. Create a **new** `sonarqube_extensions_8_x` volume. 

2. If you're using Oracle, copy the JDBC driver into the new `sonarqube_extensions_8_x` volume.

3. Non-default plugins need to be either re-added through the marketplace after start-up or manually added to the `sonarqube_extensions_8_x` volume. 

4. Stop and remove the SonarQube container (a restart from the UI is not enough as the environment variables are only evaluated during the first run, not during a restart):

	```console
	$ docker stop <image_name>
	$ docker rm <image_name>
	```

5. Run docker:

	```bash
	$> docker run -d --name sonarqube \
		-p 9000:9000 \
		-e SONAR_JDBC_URL=... \
		-e SONAR_JDBC_USERNAME=... \
		-e SONAR_JDBC_PASSWORD=... \
		-v sonarqube_data:/opt/sonarqube/data \
		-v sonarqube_extensions_8_x:/opt/sonarqube/extensions \
		-v sonarqube_logs:/opt/sonarqube/logs \
		<image_name>
	```

6. Browse to `http://yourSonarQubeServerURL/setup` and follow the setup instructions.

7. Reanalyze your projects to get fresh data.

### From 7.9.x LTS to another 7.9.x LTS version

No specific Docker operations are needed, just use the new tag.


## Additional Information

### Oracle Clean-up

Starting with version 6.6, there's an additional step you may want to perform if you're using Oracle. On Oracle, the database columns to be dropped are now marked as UNUSED and are not physically dropped anymore. To reclaim disk space, Oracle administrators must drop these unused columns manually. The SQL request is `ALTER TABLE foo DROP UNUSED COLUMNS`. The relevant tables are listed in the system table `all_unused_col_tabs`.

### Additional Database Maintenance

Refreshing your database's statistics and rebuilding your database's indices are recommended once the technical upgrade is done (just before the very last step).

For PostgreSQL, that means executing `VACUUM FULL`. According to the PostgreSQL documentation:

```
In normal PostgreSQL operation, tuples that are deleted or obsoleted by an update are not physically removed from their table; they remain present until a VACUUM is done.
```

### Scanner Update

When upgrading SonarQube, you should also make sure you’re using the latest versions of the SonarQube scanners to take advantage of features and fixes on the scanner side. Please check the documentation pages of the Scanners you use for the most recent version compatible with SonarQube and your build tools.

### SonarQube as a Linux or Windows Service

If you use external configuration, such as a script or Windows Service to control your server, you'll need to update it to point to `$NEW_SONARQUBE_HOME`.

In case you used the InstallNTService.bat to install SonarQube as a Windows Service, run the $OLD_SONARQUBE_HOME/bin/.../UninstallNTService.bat before running the InstallNTService.bat of the $NEW_SONARQUBE_HOME.

## Release Upgrade Notes

Usually SonarQube releases come with some specific recommendations for upgrading from the previous version. You should read the upgrade notes for each version between your current version and the target version.

<!-- /sonarqube -->
