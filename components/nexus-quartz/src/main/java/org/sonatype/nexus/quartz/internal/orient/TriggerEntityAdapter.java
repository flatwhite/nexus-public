/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.quartz.internal.orient;

import java.util.List;
import java.util.function.Predicate;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.orient.OClassNameBuilder;
import org.sonatype.nexus.orient.OIndexNameBuilder;
import org.sonatype.nexus.orient.entity.action.BrowseEntitiesByPropertyAction;
import org.sonatype.nexus.orient.entity.action.BrowseEntitiesWithPredicateAction;
import org.sonatype.nexus.orient.entity.action.DeleteEntitiesAction;
import org.sonatype.nexus.orient.entity.marshal.FieldObjectMapper;
import org.sonatype.nexus.orient.entity.marshal.JacksonMarshaller;
import org.sonatype.nexus.orient.entity.marshal.MarshalledEntityAdapter;
import org.sonatype.nexus.orient.entity.marshal.Marshaller;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OClass.INDEX_TYPE;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import org.quartz.CronExpression;
import org.quartz.JobKey;
import org.quartz.TriggerKey;
import org.quartz.spi.OperableTrigger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link TriggerEntity} adapter.
 *
 * @since 3.0
 */
@Named
@Singleton
public class TriggerEntityAdapter
    extends MarshalledEntityAdapter<TriggerEntity>
{
  private static final String DB_CLASS = new OClassNameBuilder()
      .prefix("quartz")
      .type("trigger")
      .build();

  private static final String P_NAME = "name";

  private static final String P_GROUP = "group";

  private static final String P_JOB_NAME = "job_name";

  private static final String P_JOB_GROUP = "job_group";

  private static final String P_CALENDAR_NAME = "calendar_name";

  private static final String P_STATE = "state";

  private static final String I_NAME_GROUP = new OIndexNameBuilder()
      .type(DB_CLASS)
      .property(P_NAME)
      .property(P_GROUP)
      .build();

  private static final String I_JOB_NAME_JOB_GROUP = new OIndexNameBuilder()
      .type(DB_CLASS)
      .property(P_JOB_NAME)
      .property(P_JOB_GROUP)
      .build();

  private static final String I_CALENDAR_NAME = new OIndexNameBuilder()
      .type(DB_CLASS)
      .property(P_CALENDAR_NAME)
      .build();

  private static final String I_STATE = new OIndexNameBuilder()
      .type(DB_CLASS)
      .property(P_STATE)
      .build();

  public TriggerEntityAdapter() {
    super(DB_CLASS, createMarshaller(), OperableTrigger.class.getClassLoader());
  }

  private static Marshaller createMarshaller() {
    return new JacksonMarshaller(new FieldObjectMapper()
        // special handling for CronExpression deserialization
        .addMixIn(CronExpression.class, CronExpressionMixin.class)
    );
  }

  @Override
  protected void defineType(final OClass type) {
    super.defineType(type);

    type.createProperty(P_NAME, OType.STRING)
        .setMandatory(true)
        .setNotNull(true);
    type.createProperty(P_GROUP, OType.STRING)
        .setMandatory(true)
        .setNotNull(false); // nullable
    type.createProperty(P_JOB_NAME, OType.STRING)
        .setMandatory(true)
        .setNotNull(true);
    type.createProperty(P_JOB_GROUP, OType.STRING)
        .setMandatory(true)
        .setNotNull(false); // nullable
    type.createProperty(P_CALENDAR_NAME, OType.STRING)
        .setMandatory(true)
        .setNotNull(false); // nullable
    type.createProperty(P_STATE, OType.STRING)
        .setMandatory(true)
        .setNotNull(true);

    type.createIndex(I_NAME_GROUP, INDEX_TYPE.UNIQUE, P_NAME, P_GROUP);
    type.createIndex(I_JOB_NAME_JOB_GROUP, INDEX_TYPE.NOTUNIQUE, P_JOB_NAME, P_JOB_GROUP);
    type.createIndex(I_CALENDAR_NAME, INDEX_TYPE.NOTUNIQUE, P_CALENDAR_NAME);
    type.createIndex(I_STATE, INDEX_TYPE.NOTUNIQUE, P_STATE);
  }

  @Override
  protected TriggerEntity newEntity() {
    return new TriggerEntity();
  }

  @Override
  protected void readFields(final ODocument document, final TriggerEntity entity) throws Exception {
    super.readFields(document, entity);

    entity.setName(document.field(P_NAME));
    entity.setGroup(document.field(P_GROUP));
    entity.setJobName(document.field(P_JOB_NAME));
    entity.setJobGroup(document.field(P_JOB_GROUP));
    entity.setCalendarName(document.field(P_CALENDAR_NAME));
    entity.setState(TriggerEntity.State.valueOf(document.field(P_STATE)));
  }

  @Override
  protected void writeFields(final ODocument document, final TriggerEntity entity) throws Exception {
    super.writeFields(document, entity);

    document.field(P_NAME, entity.getName());
    document.field(P_GROUP, entity.getGroup());
    document.field(P_JOB_NAME, entity.getJobName());
    document.field(P_JOB_GROUP, entity.getJobGroup());
    document.field(P_CALENDAR_NAME, entity.getCalendarName());
    document.field(P_STATE, entity.getState().name());
  }

  //
  // Actions
  //

  /**
   * Read a single entity matching {@link TriggerKey}.
   */
  public final ReadEntityByKeyAction<TriggerEntity> readyByKey =
      new ReadEntityByKeyAction<>(this, P_NAME, P_GROUP);

  /**
   * Check if an entity exists for a {@link TriggerKey}.
   */
  public final ExistsByKeyAction existsByKey = new ExistsByKeyAction(this, P_NAME, P_GROUP);

  /**
   * Browse all entities which have a matching state property.
   */
  public final BrowseEntitiesByPropertyAction<TriggerEntity> browseByState =
      new BrowseEntitiesByPropertyAction<>(this, P_STATE);

  /**
   * Browse all entities which have a matching group property.
   */
  public final BrowseEntitiesByPropertyAction<TriggerEntity> browseByGroup =
      new BrowseEntitiesByPropertyAction<>(this, P_GROUP);

  /**
   * Browse all entities which have a matching calendar-name property.
   */
  public final BrowseEntitiesByPropertyAction<TriggerEntity> browseByCalendarName =
      new BrowseEntitiesByPropertyAction<>(this, P_CALENDAR_NAME);

  /**
   * Browse all entities matching given {@link Predicate}.
   */
  public final BrowseEntitiesWithPredicateAction<TriggerEntity> browseWithPredicate =
      new BrowseEntitiesWithPredicateAction<>(this);

  /**
   * Delete a single entity matching {@link TriggerKey}.
   */
  public final DeleteEntityByKeyAction deleteByKey = new DeleteEntityByKeyAction(this, P_NAME, P_GROUP);

  /**
   * Delete all entities.
   */
  public final DeleteEntitiesAction deleteAll = new DeleteEntitiesAction(this);

  private static final String BROWSE_BY_JOB_KEY_QUERY = String.format("SELECT FROM %s WHERE %s = ? AND %s = ?",
      DB_CLASS, P_JOB_NAME, P_JOB_GROUP);

  /**
   * Browse all entities matching {@link JobKey}.
   */
  public Iterable<TriggerEntity> browseByJobKey(final ODatabaseDocumentTx db, final JobKey jobKey) {
    checkNotNull(db);
    checkNotNull(jobKey);

    List<ODocument> results = db.command(new OSQLSynchQuery<>(BROWSE_BY_JOB_KEY_QUERY))
        .execute(jobKey.getName(), jobKey.getGroup());

    return transform(results);
  }

  private static final String DELETE_BY_JOB_KEY_QUERY = String.format("DELETE FROM %s WHERE %s = ? AND %s = ?",
      DB_CLASS, P_JOB_NAME, P_JOB_GROUP);

  /**
   * Delete all entities matching {@link JobKey}.
   */
  public void deleteByJobKey(final ODatabaseDocumentTx db, final JobKey jobKey) {
    checkNotNull(db);
    checkNotNull(jobKey);

    db.command(new OCommandSQL(DELETE_BY_JOB_KEY_QUERY)).execute(jobKey.getName(), jobKey.getGroup());
  }
}
