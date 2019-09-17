/*
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.omnifaces.elios.config.helper;

import java.security.Principal;

/**
 * Principal that represents the caller principal associated with the invocation being
 * processed by the container (e.g. the current HTTP request).
 */
public class CallerPrincipal implements Principal {

    private static final long serialVersionUID = -6951555039431667786L;

    private final String name;

    public CallerPrincipal(String name) {
        this.name = name;
    }

    /**
     * The name of the caller
     *
     * @return The name of the caller
     */
    @Override
    public String getName() {
        return name;
    }

}
