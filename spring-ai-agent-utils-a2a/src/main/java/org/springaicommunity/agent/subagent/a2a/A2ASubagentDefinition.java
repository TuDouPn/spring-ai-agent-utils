/*
* Copyright 2026 - 2026 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springaicommunity.agent.subagent.a2a;


import java.util.Objects;

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.springaicommunity.agent.common.task.subagent.SubagentDefinition;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;

/**
 * A2A protocol subagent definition wrapping an AgentCard.
 * Demonstrates how to implement {@link SubagentDefinition} for remote agent protocols.
 *
 * @author Christian Tzolov
 * @see <a href="https://google.github.io/A2A/">A2A Protocol Specification</a>
 */
public class A2ASubagentDefinition implements SubagentDefinition {

	public static final String KIND = "A2A";

	private final SubagentReference subagentRef;

	private final AgentCard card;

	private final String description;

	public A2ASubagentDefinition(SubagentReference subagentRef, AgentCard card) {
		this.subagentRef = Objects.requireNonNull(subagentRef, "subagentRef must not be null");
		this.card = Objects.requireNonNull(card, "card must not be null");
		this.description = buildDescription(card);
	}

	@Override
	public String getName() {
		return card.name();
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public String getKind() {
		return KIND;
	}

	@Override
	public SubagentReference getReference() {
		return subagentRef;
	}

	public AgentCard getAgentCard() {
		return card;
	}

	/**
	 * Builds the description from the card description and its skills. Skills are
	 * indented so they read as part of this agent's entry in the Task tool's agent list,
	 * and listed under "Can help with" rather than "Skills" so the model does not confuse
	 * them with Agent Skills invoked through the Skill tool.
	 */
	private static String buildDescription(AgentCard card) {
		StringBuilder sb = new StringBuilder(card.description());
		if (!card.skills().isEmpty()) {
			sb.append("\n  Can help with:");
			for (AgentSkill skill : card.skills()) {
				sb.append("\n  - ").append(skill.name()).append(": ").append(skill.description());
				if (skill.examples() != null && !skill.examples().isEmpty()) {
					sb.append("\n    Examples: ").append(String.join("; ", skill.examples()));
				}
			}
		}
		return sb.toString();
	}

}
