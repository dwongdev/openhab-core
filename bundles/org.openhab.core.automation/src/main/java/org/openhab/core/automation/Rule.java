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
package org.openhab.core.automation;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.template.RuleTemplate;
import org.openhab.core.common.registry.Identifiable;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.Configuration;

/**
 * An automation Rule is built from {@link Module}s and consists of three parts:
 * <ul>
 * <li><b>Triggers:</b> a list of {@link Trigger} modules. Each {@link Trigger} from this list
 * can start the evaluation of the Rule. A Rule with an empty list of {@link Trigger}s can
 * only be triggered through the {@link RuleManager#runNow(String, boolean, java.util.Map)} method,
 * or directly executed with the {@link RuleManager#runNow(String)} method.
 * <li><b>Conditions:</b> a list of {@link Condition} modules. When a Rule is triggered, the
 * evaluation of the Rule {@link Condition}s will determine if the Rule will be executed.
 * A Rule will be executed only when all it's {@link Condition}s are satisfied. If the {@link Condition}s
 * list is empty, the Rule is considered satisfied.
 * <li><b>Actions:</b> a list of {@link Action} modules. These modules determine the actions that
 * will be performed when a Rule is executed.
 * </ul>
 * Additionally, Rules can have <code><b>tags</b></code> - non-hierarchical keywords or terms for describing them.
 * They can help the user to classify or label the Rules, and to filter and search them.
 *
 * @author Kai Kreuzer - Initial contribution
 * @author Ravi Nadahar - Added TemplateState
 */
@NonNullByDefault
public interface Rule extends Identifiable<String> {

    /**
     * This method is used to obtain the identifier of the Rule. It can be specified by the {@link Rule}'s
     * creator, or randomly generated.
     *
     * @return an identifier of this {@link Rule}. Can't be {@code null}.
     */
    @Override
    String getUID();

    /**
     * This method is used to obtain the {@link RuleTemplate} identifier of the template the {@link Rule} was created
     * from. It will be used by the {@link RuleRegistry} to resolve the {@link Rule}: to validate the {@link Rule}'s
     * configuration, as well as to create and configure the {@link Rule}'s modules. If a {@link Rule} has not been
     * created from a template, or has been successfully resolved by the {@link RuleRegistry}, this method will return
     * {@code null}.
     *
     * @return the identifier of the {@link Rule}'s {@link RuleTemplate}, or {@code null} if the {@link Rule} has not
     *         been created from a template, or has been successfully resolved by the {@link RuleRegistry}.
     */
    @Nullable
    String getTemplateUID();

    /**
     * This method is used to track the template processing state by the {@link RuleRegistry}. The default
     * implementation doesn't support templates and must be overridden if the {@link Rule} implementation
     * supports templates.
     *
     * @return the current template processing state.
     */
    default TemplateState getTemplateState() {
        return TemplateState.NO_TEMPLATE;
    }

    /**
     * This method is used to obtain the {@link Rule}'s human-readable name.
     *
     * @return the {@link Rule}'s human-readable name, or {@code null}.
     */
    @Nullable
    String getName();

    /**
     * This method is used to obtain the {@link Rule}'s assigned tags.
     *
     * @return the {@link Rule}'s assigned tags.
     */
    Set<String> getTags();

    /**
     * This method is used to obtain the human-readable description of the purpose and consequences of the
     * {@link Rule}'s execution.
     *
     * @return the {@link Rule}'s human-readable description, or {@code null}.
     */
    @Nullable
    String getDescription();

    /**
     * This method is used to obtain the {@link Rule}'s {@link Visibility}.
     *
     * @return the {@link Rule}'s {@link Visibility} value.
     */
    Visibility getVisibility();

    /**
     * This method is used to obtain the {@link Rule}'s {@link Configuration}.
     *
     * @return current configuration values, or an empty {@link Configuration}.
     */
    Configuration getConfiguration();

    /**
     * This method is used to obtain the {@link List} with {@link ConfigDescriptionParameter}s defining meta info for
     * configuration properties of the {@link Rule}.
     *
     * @return a {@link List} of {@link ConfigDescriptionParameter}s.
     */
    List<ConfigDescriptionParameter> getConfigurationDescriptions();

    /**
     * This method is used to get the conditions participating in {@link Rule}.
     *
     * @return a list with the conditions that belong to this {@link Rule}.
     */
    List<Condition> getConditions();

    /**
     * This method is used to get the actions participating in {@link Rule}.
     *
     * @return a list with the actions that belong to this {@link Rule}.
     */
    List<Action> getActions();

    /**
     * This method is used to get the triggers participating in {@link Rule}.
     *
     * @return a list with the triggers that belong to this {@link Rule}.
     */
    List<Trigger> getTriggers();

    /**
     * Obtains the modules of the {@link Rule}.
     *
     * @return the modules of the {@link Rule} or empty list if the {@link Rule} has no modules.
     */
    List<Module> getModules();

    /**
     * This method is used to get a {@link Module} participating in {@link Rule}
     *
     * @param moduleId specifies the id of a module belonging to this {@link Rule}.
     * @return module with specified id or {@code null} if it does not belong to this {@link Rule}.
     */
    default @Nullable Module getModule(String moduleId) {
        for (Module module : getModules()) {
            if (module.getId().equals(moduleId)) {
                return module;
            }
        }
        return null;
    }

    /**
     * This enum represent the different states a rule can have in respect to rule templates.
     */
    public enum TemplateState {

        /** This {@link Rule} isn't associated with a template */
        NO_TEMPLATE,

        /** This {@link Rule} is associated with a template and it has yet to be instantiated */
        PENDING,

        /** This {@link Rule} is associated with a template that wasn't found */
        TEMPLATE_MISSING,

        /** This {@link Rule} is associated with a template and has been instantiated */
        INSTANTIATED;

        @Override
        public String toString() {
            switch (this) {
                case INSTANTIATED:
                    return "instantiated";
                case PENDING:
                    return "pending";
                case TEMPLATE_MISSING:
                    return "template-missing";
                case NO_TEMPLATE:
                default:
                    return "no-template";
            }
        }

        /**
         * Returns the {@link TemplateState} that best represents the specified string. If no match is found,
         * {@link TemplateState#NO_TEMPLATE} is returned.
         *
         * @param templateState the string to convert.
         * @return The resulting {@link TemplateState}.
         */
        public static TemplateState typeOf(@Nullable String templateState) {
            if (templateState == null) {
                return NO_TEMPLATE;
            }
            String s = templateState.trim().toLowerCase(Locale.ROOT);
            switch (s) {
                case "instantiated":
                    return INSTANTIATED;
                case "pending":
                    return PENDING;
                case "template-missing":
                    return TEMPLATE_MISSING;
                default:
                    return NO_TEMPLATE;
            }
        }
    }
}
