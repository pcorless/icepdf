/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.tag.query;

/**
 * @author mcollette
 * @since 4.0
 */
public abstract class Function implements Expression {
    protected String[] arguments;

    public void setArguments(String[] arguments) {
        this.arguments = arguments;
    }

    public String describe(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++)
            sb.append("  ");
        String className = getClass().getName();
        className = className.substring(className.lastIndexOf(".") + 1);
        sb.append(className);
        sb.append(" ( ");
        int num = (arguments != null) ? arguments.length : 0;
        for (int i = 0; i < num; i++) {
            sb.append('\'');
            sb.append(arguments[i]);
            sb.append('\'');
            if (i < (num - 1))
                sb.append(", ");
        }
        sb.append(" )\n");
        return sb.toString();
    }
}
