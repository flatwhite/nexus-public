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
package org.sonatype.nexus.internal.orient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.common.entity.Entity;
import org.sonatype.nexus.common.event.EventBus;
import org.sonatype.nexus.common.event.EventBusImpl;
import org.sonatype.nexus.orient.DatabaseInstanceRule;
import org.sonatype.nexus.orient.OClassNameBuilder;
import org.sonatype.nexus.orient.entity.EntityAdapter;
import org.sonatype.nexus.orient.entity.EntityEvent;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.hook.ORecordHook.HOOK_POSITION;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.impl.ODocument;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link EntityHook}.
 */
public class EntityHookTest
    extends TestSupport
{
  @Rule
  public DatabaseInstanceRule sendingDatabase = new DatabaseInstanceRule("sender");

  @Rule
  public DatabaseInstanceRule receivingDatabase = new DatabaseInstanceRule("receiver");

  TestEntityAdapter entityAdapter = new TestEntityAdapter();

  TestSubscriber subscriber = new TestSubscriber();

  EntityHook entityHook;

  static class TestEntity
      extends Entity
  {
    // nothing to add
  }

  static class TestEntityAdapter
      extends EntityAdapter<TestEntity>
  {
    static final String DB_CLASS = new OClassNameBuilder().type("test").build();

    TestEntityAdapter() {
      super(DB_CLASS);
    }

    @Override
    protected TestEntity newEntity() {
      return new TestEntity();
    }

    @Override
    protected void defineType(OClass type) {
      // nothing to add
    }

    @Override
    protected void writeFields(ODocument document, TestEntity entity) throws Exception {
      // nothing to add
    }

    @Override
    protected void readFields(ODocument document, TestEntity entity) throws Exception {
      // nothing to add
    }

    @Override
    public boolean sendEvents() {
      return true;
    }
  }

  class TestSubscriber
  {
    List<EntityEvent> events = new ArrayList<>();

    @Subscribe
    @AllowConcurrentEvents
    public void on(EntityEvent event) {
      // store events while connected to a different db than the sender
      try (ODatabaseDocumentTx db = receivingDatabase.getInstance().acquire()) {
        db.begin();
        events.add(event);
        db.commit();
      }
    }
  }

  @Before
  public void setup() throws Exception {
    EventBus eventBus = new EventBusImpl("reentrant");
    entityHook = new EntityHook(eventBus, Arrays.asList(entityAdapter));
    eventBus.register(subscriber);
  }

  @Test
  public void test() {
    EntityEvent event;

    try (ODatabaseDocumentTx db = sendingDatabase.getInstance().acquire()) {

      // connect up the entity hook (this is normally done in the server, but we're using a test instance here)
      db.registerListener(entityHook);
      db.registerHook(entityHook, HOOK_POSITION.LAST);

      entityAdapter.register(db);

      // CREATE
      db.begin();
      ODocument firstEntity = entityAdapter.addEntity(db, new TestEntity());
      ODocument secondEntity = entityAdapter.addEntity(db, new TestEntity());
      db.commit();

      assertThat(subscriber.events, hasSize(2));

      event = subscriber.events.get(0);
      assertThat(event.getClass().getSimpleName(), is("EntityCreatedEvent"));
      assertThat(entityAdapter.recordIdentity(event.getId()), is(firstEntity));

      event = subscriber.events.get(1);
      assertThat(event.getClass().getSimpleName(), is("EntityCreatedEvent"));
      assertThat(entityAdapter.recordIdentity(event.getId()), is(secondEntity));

      // UPDATE
      db.begin();
      entityAdapter.writeEntity(firstEntity, new TestEntity());
      entityAdapter.writeEntity(secondEntity, new TestEntity());
      db.commit();

      assertThat(subscriber.events, hasSize(4));

      event = subscriber.events.get(2);
      assertThat(event.getClass().getSimpleName(), is("EntityUpdatedEvent"));
      assertThat(entityAdapter.recordIdentity(event.getId()), is(firstEntity));

      event = subscriber.events.get(3);
      assertThat(event.getClass().getSimpleName(), is("EntityUpdatedEvent"));
      assertThat(entityAdapter.recordIdentity(event.getId()), is(secondEntity));

      // DELETE
      db.begin();
      entityAdapter.deleteEntity(db, entityAdapter.readEntity(firstEntity));
      entityAdapter.deleteEntity(db, entityAdapter.readEntity(secondEntity));
      db.commit();

      assertThat(subscriber.events, hasSize(6));

      event = subscriber.events.get(4);
      assertThat(event.getClass().getSimpleName(), is("EntityDeletedEvent"));
      assertThat(entityAdapter.recordIdentity(event.getId()), is(firstEntity));

      event = subscriber.events.get(5);
      assertThat(event.getClass().getSimpleName(), is("EntityDeletedEvent"));
      assertThat(entityAdapter.recordIdentity(event.getId()), is(secondEntity));
    }
  }
}
