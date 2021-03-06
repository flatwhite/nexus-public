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
package org.sonatype.nexus.selector;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * {@link Selector} implementation that uses JEXL to evaluate expressions describing the selection criteria.
 *
 * @see <a href="http://commons.apache.org/proper/commons-jexl/">Commons Jexl</a>
 *
 * @since 3.0
 */
public class JexlSelector
    implements Selector
{
  private static final JexlEngine engine = new JexlEngine();
  private final Optional<Expression> expression;

  public JexlSelector(final String expression) {
    this.expression = isNullOrEmpty(expression) ? Optional.<Expression>empty() : Optional.of(engine.createExpression(expression));
  }

  @Override
  public boolean evaluate(final VariableSource variableSource) {
    if (expression.isPresent()) {
      Set<String> vars = variableSource.getVariableSet();
      JexlContext jc = new MapContext();

      // load the values, if present, into the context
      vars.forEach(variable -> variableSource.get(variable).ifPresent(value -> jc.set(variable, value)));

      Object o = expression.get().evaluate(jc);

      return (o instanceof Boolean) ? (Boolean) o : false;
    }
    else {
      return true;
    }
  }

  @Override
  public String toString() {
    return expression.isPresent() ? expression.get().dump() : "";
  }
}
