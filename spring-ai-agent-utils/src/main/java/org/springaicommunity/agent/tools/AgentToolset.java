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
package org.springaicommunity.agent.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springaicommunity.agent.common.exec.ExecBackend;
import org.springaicommunity.agent.common.workspace.Workspace;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.util.Assert;

/**
 * Builds the default agent tool callbacks for an {@link ExecBackend} and
 * {@link Workspace} pair: shell ({@code Bash}, {@code BashOutput}, {@code KillShell}),
 * file ({@code Read}, {@code Write}, {@code Edit}) and search ({@code Grep},
 * {@code Glob}, {@code ListDirectory}).
 *
 * <p>
 * Omitting the backend and the workspace keeps each tool's own default (local execution,
 * no directory confinement). When both are set, shell commands go through the backend and
 * the file/search tools are confined to the workspace — the sandbox recipe. Tools that
 * need their own configuration (skills, web search, {@code TodoWrite},
 * {@code AskUserQuestion}) are not part of the default set; add them with
 * {@link Builder#with(Object)}.
 *
 * <pre>{@code
 * List<ToolCallback> tools = AgentToolset.builder()
 *     .execBackend(backend)
 *     .workspace(workspace)
 *     .listener(eventLogListener)
 *     .build();
 * }</pre>
 *
 * {@link WorkspaceAccess} selects a permission tier. {@code READ} publishes only the
 * read-only callbacks (including {@code Read}, and not {@code Write}/{@code Edit}). That
 * is assembly filtering: {@link FileSystemTools} itself is unchanged.
 *
 * @see WorkspaceAccess
 */
public final class AgentToolset {

	private AgentToolset() {
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * How much of the default toolset is published.
	 */
	public enum WorkspaceAccess {

		/** No built-in shell, file, or search tools. */
		NONE,

		/** Read-only workspace tools: {@code Read}, {@code Grep}, {@code Glob}, {@code ListDirectory}. */
		READ,

		/** {@link #READ} plus file mutation: {@code Write} and {@code Edit}. */
		WRITE,

		/** {@link #WRITE} plus shell execution: {@code Bash}, {@code BashOutput}, {@code KillShell}. */
		EXECUTE

	}

	public static final class Builder {

		private static final List<String> SHELL_TOOLS = List.of("Bash", "BashOutput", "KillShell");

		private static final List<String> READ_FILE_TOOLS = List.of("Read");

		private static final List<String> WRITE_FILE_TOOLS = List.of("Write", "Edit");

		private ExecBackend execBackend;

		private Workspace workspace;

		private ToolCallListener listener;

		private WorkspaceAccess workspaceAccess = WorkspaceAccess.EXECUTE;

		private final List<Object> additionalTools = new ArrayList<>();

		private final Set<String> excludedToolNames = new LinkedHashSet<>();

		private Builder() {
		}

		/**
		 * Backend for {@code Bash}, {@code BashOutput} and {@code KillShell}. Used only
		 * at {@link WorkspaceAccess#EXECUTE}. When unset, shell commands run on the host
		 * (rooted at {@link #workspace(Workspace)} when that is set).
		 */
		public Builder execBackend(ExecBackend execBackend) {
			Assert.notNull(execBackend, "execBackend must not be null");
			this.execBackend = execBackend;
			return this;
		}

		/**
		 * Workspace for the file and search tools (working directory and confinement).
		 * At {@link WorkspaceAccess#EXECUTE}, also the local shell working directory
		 * when no {@link #execBackend(ExecBackend)} is set. A custom backend keeps its
		 * own working directory.
		 */
		public Builder workspace(Workspace workspace) {
			Assert.notNull(workspace, "workspace must not be null");
			this.workspace = workspace;
			return this;
		}

		/**
		 * Observes every callback this builder returns. Same effect as
		 * {@link ToolCallListeners#wrapAll(List, ToolCallListener)} on the finished list.
		 */
		public Builder listener(ToolCallListener listener) {
			Assert.notNull(listener, "listener must not be null");
			this.listener = listener;
			return this;
		}

		/**
		 * Permission tier. Default: {@link WorkspaceAccess#EXECUTE}.
		 */
		public Builder workspaceAccess(WorkspaceAccess workspaceAccess) {
			Assert.notNull(workspaceAccess, "workspaceAccess must not be null");
			this.workspaceAccess = workspaceAccess;
			return this;
		}

		/**
		 * Appends a tool that is not part of the default set. An annotated tool object
		 * is expanded with {@link ToolCallbacks#from(Object...)}; a {@link ToolCallback}
		 * or {@link ToolCallbackProvider} is added as-is. Extras are appended after the
		 * built-in callbacks, then {@link #without(String)} is applied.
		 */
		public Builder with(Object tool) {
			Assert.notNull(tool, "tool must not be null");
			this.additionalTools.add(tool);
			return this;
		}

		/**
		 * Drops a callback by its tool name (for example {@code "Grep"} or
		 * {@code "Bash"}). The name must be present on the list that would otherwise be
		 * returned, including tools added with {@link #with(Object)}.
		 */
		public Builder without(String toolName) {
			Assert.hasText(toolName, "toolName must not be empty");
			this.excludedToolNames.add(toolName);
			return this;
		}

		/**
		 * @return an immutable list of callbacks, listener-wrapped when a listener was
		 * set
		 */
		public List<ToolCallback> build() {
			List<ToolCallback> callbacks = new ArrayList<>();
			if (this.workspaceAccess != WorkspaceAccess.NONE) {
				if (this.workspaceAccess == WorkspaceAccess.EXECUTE) {
					callbacks.addAll(shellCallbacks());
				}
				callbacks.addAll(fileSystemCallbacks());
				callbacks.addAll(searchCallbacks());
			}
			for (Object extra : this.additionalTools) {
				callbacks.addAll(callbacksFrom(extra));
			}
			if (!this.excludedToolNames.isEmpty()) {
				applyExclusions(callbacks);
			}
			List<ToolCallback> built = List.copyOf(callbacks);
			if (this.listener != null) {
				return ToolCallListeners.wrapAll(built, this.listener);
			}
			return built;
		}

		private List<ToolCallback> shellCallbacks() {
			ShellTools.Builder shell = ShellTools.builder();
			if (this.execBackend != null) {
				shell.execBackend(this.execBackend);
			}
			else if (this.workspace != null) {
				shell.workspace(this.workspace);
			}
			return select(shell.build(), SHELL_TOOLS);
		}

		private List<ToolCallback> fileSystemCallbacks() {
			FileSystemTools.Builder files = FileSystemTools.builder();
			if (this.workspace != null) {
				files.workspace(this.workspace);
			}
			List<String> names = new ArrayList<>(READ_FILE_TOOLS);
			if (this.workspaceAccess == WorkspaceAccess.WRITE || this.workspaceAccess == WorkspaceAccess.EXECUTE) {
				names.addAll(WRITE_FILE_TOOLS);
			}
			return select(files.build(), names);
		}

		private List<ToolCallback> searchCallbacks() {
			GrepTool.Builder grep = GrepTool.builder();
			GlobTool.Builder glob = GlobTool.builder();
			ListDirectoryTool.Builder listDirectory = ListDirectoryTool.builder();
			if (this.workspace != null) {
				grep.workspace(this.workspace);
				glob.workspace(this.workspace);
				listDirectory.workspace(this.workspace);
			}
			List<ToolCallback> callbacks = new ArrayList<>();
			callbacks.addAll(select(grep.build(), List.of("Grep")));
			callbacks.addAll(select(glob.build(), List.of("Glob")));
			callbacks.addAll(select(listDirectory.build(), List.of("ListDirectory")));
			return callbacks;
		}

		private void applyExclusions(List<ToolCallback> callbacks) {
			List<String> available = callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList();
			List<String> missing = new ArrayList<>();
			for (String name : this.excludedToolNames) {
				if (!available.contains(name)) {
					missing.add(name);
				}
			}
			if (!missing.isEmpty()) {
				throw new IllegalArgumentException("Unknown tool name(s) for without(...): " + missing
						+ ". Available tools: " + available);
			}
			callbacks.removeIf(callback -> this.excludedToolNames.contains(callback.getToolDefinition().name()));
		}

		private static List<ToolCallback> select(Object tool, List<String> namesInOrder) {
			Map<String, ToolCallback> byName = new LinkedHashMap<>();
			for (ToolCallback callback : ToolCallbacks.from(tool)) {
				byName.put(callback.getToolDefinition().name(), callback);
			}
			List<ToolCallback> selected = new ArrayList<>(namesInOrder.size());
			for (String name : namesInOrder) {
				ToolCallback callback = byName.get(name);
				Assert.notNull(callback, "Expected tool '" + name + "' on " + tool.getClass().getName() + " but found "
						+ byName.keySet());
				selected.add(callback);
			}
			return selected;
		}

		private static List<ToolCallback> callbacksFrom(Object tool) {
			if (tool instanceof ToolCallback callback) {
				return List.of(callback);
			}
			if (tool instanceof ToolCallbackProvider provider) {
				ToolCallback[] callbacks = provider.getToolCallbacks();
				Assert.notNull(callbacks, "ToolCallbackProvider returned null");
				return Arrays.asList(callbacks);
			}
			return Arrays.asList(ToolCallbacks.from(tool));
		}

	}

}
