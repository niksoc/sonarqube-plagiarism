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
package org.sonar.server.es;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.server.es.IndexType.IndexMainType;
import org.sonar.server.es.metadata.MetadataIndex;
import org.sonar.server.es.metadata.MetadataIndexDefinition;
import org.sonar.server.es.metadata.MetadataIndexImpl;
import org.sonar.server.es.newindex.NewRegularIndex;
import org.sonar.server.es.newindex.SettingsConfiguration;
import org.sonar.server.platform.db.migration.es.MigrationEsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.server.es.IndexType.main;
import static org.sonar.server.es.newindex.SettingsConfiguration.newBuilder;

public class IndexCreatorTest {

  private static final SettingsConfiguration SETTINGS_CONFIGURATION = newBuilder(new MapSettings().asConfig()).build();
  private static final String LOG_DB_VENDOR_CHANGED = "Delete Elasticsearch indices (DB vendor changed)";
  private static final String LOG_DB_SCHEMA_CHANGED = "Delete Elasticsearch indices (DB schema changed)";

  @Rule
  public LogTester logTester = new LogTester();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public EsTester es = EsTester.createCustom();

  private MetadataIndexDefinition metadataIndexDefinition = new MetadataIndexDefinition(new MapSettings().asConfig());
  private MetadataIndex metadataIndex = new MetadataIndexImpl(es.client());
  private TestEsDbCompatibility esDbCompatibility = new TestEsDbCompatibility();
  private MapSettings settings = new MapSettings();
  private MigrationEsClient migrationEsClient = mock(MigrationEsClient.class);

  @Test
  public void create_index() {
    IndexCreator underTest = run(new FakeIndexDefinition());

    // check that index is created with related mapping
    verifyFakeIndexV1();

    // of course do not delete indices on stop
    underTest.stop();
    assertThat(mappings()).isNotEmpty();
  }

  @Test
  public void creation_of_new_index_is_supported_in_blue_green_deployment() {
    enableBlueGreenDeployment();

    run(new FakeIndexDefinition());

    verifyFakeIndexV1();
  }

  @Test
  public void recreate_index_on_definition_changes() {
    // v1
    run(new FakeIndexDefinition());

    IndexMainType fakeIndexType = main(Index.simple("fakes"), "fake");
    String id = "1";
    es.client().prepareIndex(fakeIndexType).setId(id).setSource(new FakeDoc().getFields()).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).get();
    assertThat(es.client().prepareGet(fakeIndexType, id).get().isExists()).isTrue();

    // v2
    run(new FakeIndexDefinitionV2());

    ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings = mappings();
    MappingMetaData mapping = mappings.get("fakes").get("fake");
    assertThat(countMappingFields(mapping)).isEqualTo(3);
    assertThat(field(mapping, "updatedAt").get("type")).isEqualTo("date");
    assertThat(field(mapping, "newField").get("type")).isEqualTo("integer");

    assertThat(es.client().prepareGet(fakeIndexType, id).get().isExists()).isFalse();
  }

  @Test
  public void fail_to_recreate_index_on_definition_changes_if_blue_green_deployment() {
    enableBlueGreenDeployment();

    // v1
    run(new FakeIndexDefinition());

    // v2
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("Blue/green deployment is not supported. Elasticsearch index [fakes] changed and needs to be dropped.");

    run(new FakeIndexDefinitionV2());
  }

  @Test
  public void reset_definition_hash_if_change_applied_during_migration_of_blue_green_deployment() {
    enableBlueGreenDeployment();

    // v1
    run(new FakeIndexDefinition());

    // v2
    when(migrationEsClient.getUpdatedIndices()).thenReturn(Sets.newHashSet(FakeIndexDefinition.INDEX_TYPE.getIndex().getName()));
    run(new FakeIndexDefinitionV2());
    // index has not been dropped-and-recreated
    verifyFakeIndexV1();
  }

  @Test
  public void mark_all_non_existing_index_types_as_uninitialized() {
    Index fakesIndex = Index.simple("fakes");
    Index fakersIndex = Index.simple("fakers");
    run(context -> {
      context.create(fakesIndex, SETTINGS_CONFIGURATION)
        .createTypeMapping(IndexType.main(fakesIndex, "fake"));
      context.create(fakersIndex, SETTINGS_CONFIGURATION)
        .createTypeMapping(IndexType.main(fakersIndex, "faker"));
    });

    assertThat(metadataIndex.getHash(fakesIndex)).isNotEmpty();
    assertThat(metadataIndex.getHash(fakersIndex)).isNotEmpty();
    assertThat(metadataIndex.getInitialized(main(fakesIndex, "fake"))).isFalse();
    assertThat(metadataIndex.getInitialized(main(fakersIndex, "faker"))).isFalse();
  }

  @Test
  public void delete_existing_indices_if_db_vendor_changed() {
    testDeleteOnDbChange(LOG_DB_VENDOR_CHANGED,
      c -> c.setHasSameDbVendor(false));
  }

  @Test
  public void do_not_check_db_compatibility_on_fresh_es() {
    // supposed to be ignored
    esDbCompatibility.setHasSameDbVendor(false);

    run(new FakeIndexDefinition());

    assertThat(logTester.logs(LoggerLevel.INFO))
      .doesNotContain(LOG_DB_VENDOR_CHANGED)
      .doesNotContain(LOG_DB_SCHEMA_CHANGED)
      .contains("Create type fakes/fake")
      .contains("Create type metadatas/metadata");
  }

  @Test
  public void start_makes_metadata_index_read_write_if_read_only() {
    run(new FakeIndexDefinition());

    IndexMainType mainType = MetadataIndexDefinition.TYPE_METADATA;
    makeReadOnly(mainType);

    run(new FakeIndexDefinition());

    assertThat(isNotReadOnly(mainType)).isTrue();
  }

  @Test
  public void start_makes_index_read_write_if_read_only() {
    FakeIndexDefinition fakeIndexDefinition = new FakeIndexDefinition();
    IndexMainType fakeIndexMainType = FakeIndexDefinition.INDEX_TYPE.getMainType();
    run(fakeIndexDefinition);

    IndexMainType mainType = MetadataIndexDefinition.TYPE_METADATA;
    makeReadOnly(mainType);
    makeReadOnly(fakeIndexMainType);

    run(fakeIndexDefinition);

    assertThat(isNotReadOnly(mainType)).isTrue();
    assertThat(isNotReadOnly(fakeIndexMainType)).isTrue();
  }

  private boolean isNotReadOnly(IndexMainType mainType) {
    String indexName = mainType.getIndex().getName();
    String readOnly = es.client().nativeClient().admin().indices().getSettings(new GetSettingsRequest().indices(indexName)).actionGet()
      .getSetting(indexName, "index.blocks.read_only_allow_delete");
    return readOnly == null;
  }

  private void makeReadOnly(IndexMainType mainType) {
    Settings.Builder builder = Settings.builder();
    builder.put("index.blocks.read_only_allow_delete", "true");
    es.client().nativeClient().admin().indices()
      .updateSettings(new UpdateSettingsRequest().indices(mainType.getIndex().getName()).settings(builder.build()))
      .actionGet();
  }

  private void enableBlueGreenDeployment() {
    settings.setProperty("sonar.blueGreenEnabled", "true");
  }

  private void testDeleteOnDbChange(String expectedLog, Consumer<TestEsDbCompatibility> afterFirstStart) {
    run(new FakeIndexDefinition());
    assertThat(logTester.logs(LoggerLevel.INFO))
      .doesNotContain(expectedLog)
      .contains("Create type fakes/fake")
      .contains("Create type metadatas/metadata");
    putFakeDocument();
    assertThat(es.countDocuments(FakeIndexDefinition.INDEX_TYPE)).isEqualTo(1);

    afterFirstStart.accept(esDbCompatibility);
    logTester.clear();
    run(new FakeIndexDefinition());

    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains(expectedLog)
      .contains("Create type fakes/fake")
      // keep existing metadata
      .doesNotContain("Create type metadatas/metadata");
    // index has been dropped and re-created
    assertThat(es.countDocuments(FakeIndexDefinition.INDEX_TYPE)).isEqualTo(0);
  }

  private ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings() {
    return es.client().nativeClient().admin().indices().prepareGetMappings().get().mappings();
  }

  @CheckForNull
  @SuppressWarnings("unchecked")
  private Map<String, Object> field(MappingMetaData mapping, String field) {
    Map<String, Object> props = (Map<String, Object>) mapping.getSourceAsMap().get("properties");
    return (Map<String, Object>) props.get(field);
  }

  private int countMappingFields(MappingMetaData mapping) {
    return ((Map) mapping.getSourceAsMap().get("properties")).size();
  }

  private IndexCreator run(IndexDefinition... definitions) {
    IndexDefinitions defs = new IndexDefinitions(definitions, new MapSettings().asConfig());
    defs.start();
    IndexCreator creator = new IndexCreator(es.client(), defs, metadataIndexDefinition, metadataIndex, migrationEsClient, esDbCompatibility, settings.asConfig());
    creator.start();
    return creator;
  }

  private void putFakeDocument() {
    es.putDocuments(FakeIndexDefinition.INDEX_TYPE, ImmutableMap.of("key", "foo"));
  }

  private void verifyFakeIndexV1() {
    ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings = mappings();
    MappingMetaData mapping = mappings.get("fakes").get("fake");
    assertThat(mapping.type()).isEqualTo("fake");
    assertThat(mapping.getSourceAsMap()).isNotEmpty();
    assertThat(countMappingFields(mapping)).isEqualTo(2);
    assertThat(field(mapping, "updatedAt").get("type")).isEqualTo("date");
  }

  private static class FakeIndexDefinition implements IndexDefinition {
    static final IndexMainType INDEX_TYPE = main(Index.simple("fakes"), "fake");

    @Override
    public void define(IndexDefinitionContext context) {
      Index index = Index.simple("fakes");
      NewRegularIndex newIndex = context.create(index, SETTINGS_CONFIGURATION);
      newIndex.createTypeMapping(IndexType.main(index, "fake"))
        .keywordFieldBuilder("key").build()
        .createDateTimeField("updatedAt");
    }
  }

  private static class FakeIndexDefinitionV2 implements IndexDefinition {
    @Override
    public void define(IndexDefinitionContext context) {
      Index index = Index.simple("fakes");
      NewRegularIndex newIndex = context.create(index, SETTINGS_CONFIGURATION);
      newIndex.createTypeMapping(IndexType.main(index, "fake"))
        .keywordFieldBuilder("key").build()
        .createDateTimeField("updatedAt")
        .createIntegerField("newField");
    }
  }
}
