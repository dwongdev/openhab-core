/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.automation.module.script.rulesupport.shared.simple;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.automation.Action;

/**
 *
 * @author Simon Merschjohann - Initial contribution
 */
@NonNullByDefault
public class SimpleRuleActionHandlerDelegate extends SimpleActionHandler {

    private SimpleRuleActionHandler handler;

    public SimpleRuleActionHandlerDelegate(SimpleRuleActionHandler handler) {
        super();
        this.handler = handler;
    }

    @Override
    public Object execute(Action module, Map<String, ?> inputs) {
        return handler.execute(module, inputs);
    }
}
