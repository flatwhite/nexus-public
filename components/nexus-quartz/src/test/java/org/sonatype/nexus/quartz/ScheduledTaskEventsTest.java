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
package org.sonatype.nexus.quartz;

import java.io.IOException;
import java.util.List;

import org.sonatype.nexus.common.event.EventBus;
import org.sonatype.nexus.scheduling.TaskInfo;
import org.sonatype.nexus.scheduling.TaskInfo.EndState;
import org.sonatype.nexus.scheduling.TaskInfo.State;
import org.sonatype.nexus.scheduling.TaskInterruptedException;
import org.sonatype.nexus.scheduling.events.TaskEvent;
import org.sonatype.nexus.scheduling.events.TaskEventCanceled;
import org.sonatype.nexus.scheduling.events.TaskEventStarted;
import org.sonatype.nexus.scheduling.events.TaskEventStoppedCanceled;
import org.sonatype.nexus.scheduling.events.TaskEventStoppedDone;
import org.sonatype.nexus.scheduling.events.TaskEventStoppedFailed;

import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import org.junit.Before;
import org.junit.Test;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

/**
 * Tests for task eventing.
 */
public class ScheduledTaskEventsTest
    extends QuartzTestSupport
{

  protected EventBus eventBus;

  protected Listener listener;

  @Before
  public void prepare() throws Exception {
    eventBus = helper().getEventBus();
    listener = new Listener();
    eventBus.register(listener);
    // reset the latch
    SleeperTask.reset();
  }

  @Test
  public void goodRun() throws Exception {
    // create the task
    final TaskInfo taskInfo = createTask(SleeperTaskDescriptor.TYPE_ID);

    // give it some time to start
    SleeperTask.youWait.await();

    // make it be done
    SleeperTask.meWait.countDown();
    Thread.yield();

    // the fact that future.get returned still does not mean that the pool is done
    // pool maintenance might not be done yet
    // so let's sleep for some
    await().atMost(RUN_TIMEOUT, MILLISECONDS).until(() -> taskScheduler().getRunningTaskCount() == 0);

    // done
    assertThat(taskScheduler().getRunningTaskCount(), equalTo(0));
    assertThat(taskInfo.getCurrentState().getState(), equalTo(State.WAITING));
    assertThat(taskInfo.getLastRunState().getEndState(), equalTo(EndState.OK));

    // started, stoppedDone
    assertThat(listener.arrivedEvents, hasSize(2));
    assertThat(listener.arrivedEvents.get(0), instanceOf(TaskEventStarted.class));
    assertThat(listener.arrivedEvents.get(1), instanceOf(TaskEventStoppedDone.class));
  }

  @Test
  public void failedRunCheckedException() throws Exception {
    SleeperTask.exception = new IOException("foo");

    // create the task
    final TaskInfo taskInfo = createTask(SleeperTaskDescriptor.TYPE_ID);

    // give it some time to start
    SleeperTask.youWait.await();

    // make it be done
    SleeperTask.meWait.countDown();
    Thread.yield();

    // the fact that future.get returned still does not mean that the pool is done
    // pool maintenance might not be done yet
    // so let's sleep for some
    await().atMost(RUN_TIMEOUT, MILLISECONDS).until(() -> taskScheduler().getRunningTaskCount() == 0);

    // done
    assertThat(taskScheduler().getRunningTaskCount(), equalTo(0));
    assertThat(taskInfo.getCurrentState().getState(), equalTo(State.WAITING));
    assertThat(taskInfo.getLastRunState().getEndState(), equalTo(EndState.FAILED));

    // started, stoppedDone
    assertThat(listener.arrivedEvents, hasSize(2));
    assertThat(listener.arrivedEvents.get(0), instanceOf(TaskEventStarted.class));
    assertThat(listener.arrivedEvents.get(1), instanceOf(TaskEventStoppedFailed.class));
    assertThat(((TaskEventStoppedFailed) listener.arrivedEvents.get(1)).getFailureCause(),
        instanceOf(IOException.class));
  }

  @Test
  public void failedRunRuntimeException() throws Exception {
    SleeperTask.exception = new IllegalArgumentException("foo");

    // create the task
    final TaskInfo taskInfo = createTask(SleeperTaskDescriptor.TYPE_ID);

    // give it some time to start
    SleeperTask.youWait.await();

    // make it be done
    SleeperTask.meWait.countDown();
    Thread.yield();

    // the fact that future.get returned still does not mean that the pool is done
    // pool maintenance might not be done yet
    // so let's sleep for some
    await().atMost(RUN_TIMEOUT, MILLISECONDS).until(() -> taskScheduler().getRunningTaskCount() == 0);

    // done
    assertThat(taskScheduler().getRunningTaskCount(), equalTo(0));
    assertThat(taskInfo.getCurrentState().getState(), equalTo(State.WAITING));
    assertThat(taskInfo.getLastRunState().getEndState(), equalTo(EndState.FAILED));

    // started, stoppedFailed
    assertThat(listener.arrivedEvents, hasSize(2));
    assertThat(listener.arrivedEvents.get(0), instanceOf(TaskEventStarted.class));
    assertThat(listener.arrivedEvents.get(1), instanceOf(TaskEventStoppedFailed.class));
    assertThat(((TaskEventStoppedFailed) listener.arrivedEvents.get(1)).getFailureCause(),
        instanceOf(IllegalArgumentException.class));
  }

  @Test
  public void canceledRunWithNonCancelableTaskWithoutInterruption() throws Exception {
    // create the task
    final TaskInfo taskInfo = createTask(SleeperTaskDescriptor.TYPE_ID);

    // give it some time to start
    SleeperTask.youWait.await();

    // attempt to cancel it 3 times (w/o interruption)
    taskInfo.getCurrentState().getFuture().cancel(false);
    taskInfo.getCurrentState().getFuture().cancel(false);
    taskInfo.getCurrentState().getFuture().cancel(false);

    // make it be done
    SleeperTask.meWait.countDown();
    Thread.yield();

    // the fact that future.get returned still does not mean that the pool is done
    // pool maintenance might not be done yet
    // so let's sleep for some
    await().atMost(RUN_TIMEOUT, MILLISECONDS).until(() -> taskScheduler().getRunningTaskCount() == 0);

    // done
    assertThat(taskScheduler().getRunningTaskCount(), equalTo(0));
    assertThat(taskInfo.getCurrentState().getState(), equalTo(State.WAITING));
    assertThat(taskInfo.getLastRunState().getEndState(), equalTo(EndState.OK));

    // started, stoppedDone: task is not cancelable, hence, is "unaware" it was
    // attempted to be canceled at all
    assertThat(listener.arrivedEvents, hasSize(2));
    assertThat(listener.arrivedEvents.get(0), instanceOf(TaskEventStarted.class));
    assertThat(listener.arrivedEvents.get(1), instanceOf(TaskEventStoppedDone.class));
  }

  @Test
  public void prematureCanceledRunWithNonCancelableTask() throws Exception {
    final TaskInfo taskInfo = createTask(SleeperTaskDescriptor.TYPE_ID, taskScheduler().getScheduleFactory().now());
    taskInfo.getCurrentState().getFuture().cancel(false);
    // do not use latches, as this task will not even start!

    // the fact that future.get returned still does not mean that the pool is done
    // pool maintenance might not be done yet
    // so let's sleep for some
    await().atMost(RUN_TIMEOUT, MILLISECONDS).until(() -> taskScheduler().getRunningTaskCount() == 0);

    // done
    assertThat(taskScheduler().getRunningTaskCount(), equalTo(0));
    assertThat(taskInfo.getCurrentState().getState(), equalTo(State.DONE));
    assertThat(taskInfo.getLastRunState().getEndState(), equalTo(EndState.CANCELED));

    // started, stoppedDone: task is not cancelable, but it was canceled by framework
    // even before it was started
    assertThat(listener.arrivedEvents, hasSize(2));
    assertThat(listener.arrivedEvents.get(0), instanceOf(TaskEventStarted.class));
    assertThat(listener.arrivedEvents.get(1), instanceOf(TaskEventStoppedCanceled.class));
  }
  
  @Test
  public void canceledRunWithNonCancelableTaskWithInterruption() throws Exception {
    final TaskInfo taskInfo = createTask(SleeperTaskDescriptor.TYPE_ID);
    cancelledRun(taskInfo, true);
  }

  @Test
  public void canceledRunWithCancelableTask() throws Exception {
    final TaskInfo taskInfo = createTask(SleeperCancelableTaskDescriptor.TYPE_ID);
    cancelledRun(taskInfo, true);
  }

  @Test
  public void canceledRunByThrowingTaskInterruptedEx() throws Exception {
    SleeperTask.exception = new TaskInterruptedException("foo", true);
    final TaskInfo taskInfo = createTask(SleeperTaskDescriptor.TYPE_ID);
    cancelledRun(taskInfo, false);
  }

  @Test
  public void canceledRunByThrowingInterruptedEx() throws Exception {
    SleeperTask.exception = new InterruptedException("foo");
    final TaskInfo taskInfo = createTask(SleeperTaskDescriptor.TYPE_ID);
    cancelledRun(taskInfo, false);
  }

  private void cancelledRun(final TaskInfo taskInfo, final boolean interrupt) throws InterruptedException {
    // give it some time to start
    SleeperTask.youWait.await();

    if (interrupt) {
      // cancel it w/ interruption
      taskInfo.getCurrentState().getFuture().cancel(true);
    }

    // make it be done
    SleeperTask.meWait.countDown();
    Thread.yield();

    // the fact that future.get returned still does not mean that the pool is done
    // pool maintenance might not be done yet
    // so let's sleep for some
    await().atMost(RUN_TIMEOUT, MILLISECONDS).until(() -> taskScheduler().getRunningTaskCount() == 0);

    // done
    assertThat(taskScheduler().getRunningTaskCount(), equalTo(0));
    assertThat(taskInfo.getCurrentState().getState(), equalTo(State.WAITING));
    assertThat(taskInfo.getLastRunState().getEndState(), equalTo(EndState.CANCELED));

    // started, stoppedDone: task is not cancelable, hence, is "unaware" it was
    // attempted to be canceled at all (no canceled events), still, end state is canceled
    // as thread was interrupted
    assertThat(listener.arrivedEvents, hasSize(3));
    assertThat(listener.arrivedEvents.get(0), instanceOf(TaskEventStarted.class));
    assertThat(listener.arrivedEvents.get(1), instanceOf(TaskEventCanceled.class));
    assertThat(listener.arrivedEvents.get(2), instanceOf(TaskEventStoppedCanceled.class));
  }

  static class Listener
  {
    final List<TaskEvent> arrivedEvents = Lists.newArrayList();

    @Subscribe
    public void on(final TaskEvent e) {
      arrivedEvents.add(e);
    }
  }
}
