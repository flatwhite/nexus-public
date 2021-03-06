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
/*global Ext, NX*/

/**
 * Migration SYNC phase screen.
 *
 * @since 3.0
 */
Ext.define('NX.coreui.migration.PhaseSyncScreen', {
  extend: 'NX.coreui.migration.ProgressScreenSupport',
  alias: 'widget.nx-coreui-migration-phasesync',

  /**
   * @override
   */
  initComponent: function () {
    var me = this;

    Ext.apply(me, {
      title: 'Synchronizing',
      description: '<p>Migration is synchronizing changes.</p>',
      buttons: [
        {
          text: 'Abort',
          action: 'abort',
          ui: 'default'
        },
        {
          text: 'Stop Monitoring',
          action: 'continue',
          ui: 'nx-primary',
          disabled: true
        },
        {
          text: 'Finish',
          action: 'finish',
          ui: 'nx-primary',
          disabled: true,
          hidden: true
        }
      ]
    });

    me.callParent();
  }
});
