# Migration Guide: 0.12.0 to 0.13.0

This release is mostly fixes: tool-call schemas and descriptions that confused models, skill loading from multiple SkillsJars, Spring Boot executable jars and GraalVM native images, relative paths in sandboxed workspaces, and the Docker exec backend on Windows. There is **one breaking API change** (`TodoWriteTool.todoWrite`) and a few behavioral changes to review.

## Dependency Version

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.13.0</version>
</dependency>
```

The Spring AI dependency is unchanged (2.0.1). The optional `spring-ai-session` dependency, used by `AutoDreamService` for cross-session recall, moves from 0.7.0 to **0.10.0**. If you use cross-session recall, upgrade `spring-ai-session` to 0.10.0 as well.

## Breaking change: `TodoWriteTool.todoWrite` takes the todo list directly

The `TodoWrite` tool's input schema was nested twice: models had to send `{"todos": {"todos": [...]}}`, while they almost always send `{"todos": [...]}`, so most calls failed to deserialize. The tool method now takes the list itself, which gives the flat schema models expect.

Model-driven use needs no change. Only code that calls `todoWrite` directly from Java, for example in tests, must be updated:

**Before (0.12.0):**

```java
todoTool.todoWrite(new Todos(List.of(
    new TodoItem("Parse settings", Status.in_progress, "Parsing settings"))));
```

**After (0.13.0):**

```java
todoTool.todoWrite(List.of(
    new TodoItem("Parse settings", Status.in_progress, "Parsing settings")));
```

The `TodoEventHandler` still receives a `Todos` instance, so event handlers are unchanged.

## Behavioral changes

### Relative paths resolve against the working directory

`GrepTool`, `GlobTool` and `ListDirectoryTool` now resolve a relative `path` argument against the configured working directory (the workspace root with `workspace(...)`, or an explicit `workingDirectory(...)`) instead of the JVM's working directory. Previously, with the JVM started outside the workspace (for example from `/` in a container), a relative path inside the workspace was denied as outside the allowed directories.

Absolute paths are unchanged, `..` components are still rejected by the confinement check, and without a configured working directory relative paths still resolve against the JVM's working directory. `FileSystemTools` (Read, Write, Edit) still requires absolute paths.

### Classpath skill locations include every classpath entry

A skills location given as a `ClassPathResource` (for example `addSkillsResource(new ClassPathResource("META-INF/skills"))`) is now collected from **every** classpath entry that contains it: the application's own classes directory and all JARs. Previously only the first match was used, so with several SkillsJars, or your own `META-INF/skills` next to a SkillsJar, all but one set of skills was silently dropped.

Things to check:

- More skills can appear than before. Use a specific classpath prefix: a generic name such as `skills` also picks up a third-party JAR that happens to contain a `skills/` folder.
- **Duplicate skill names:** when two locations define a skill with the same name, the first one registered is kept and later ones are skipped with a `WARN` log message. Previously both appeared in the tool description and the last one was invoked.
- A `ClassPathResource` is now always scanned with its own class loader rather than the thread context class loader.

### A2A subagents

- `A2ASubagentDefinition` rejects a `null` reference or agent card (`NullPointerException`).
- The subagent description now lists the agent card's skills and examples, indented under a `Can help with:` line in the `Task` tool's agent list.
- The agent card URL is now built correctly for agent URLs with a trailing slash (for example `http://host:8080/`, which previously failed), card paths without a leading `/`, and percent-encoded paths.

## Output-level changes (no code impact)

These change the tool descriptions the model sees. Review them if you assert on tool descriptions or tuned prompts around them:

- **`Read`** describes itself as a UTF-8 text reader. The inherited claims about reading images, PDFs and Jupyter notebooks, and the references to "Claude Code", are removed.
- **`Grep`, `Glob`, `ListDirectory`**: the `path` parameter is described as "absolute or relative to the working directory".
- **`Bash`**: the git commit and pull request examples no longer tell the model to add "Generated with Claude Code" or a `Co-Authored-By: Claude` trailer.
- A skill registered with the new `addSkill(...)` has no base directory, so its content is returned without the `Base directory for this skill:` line.

## Fixes (no migration required)

- **Skills in GraalVM native images**: `ClassPathResource` skill locations failed in native images with `Unsupported resource protocol for JAR loading: resource`; they now load without registering each file. `AgentUtilsRuntimeHints` also registers `.claude/skills/**/*.md`, next to `META-INF/skills/**/*.md`. Skills in other classpath locations, and non-Markdown skill files, still need resource hints from the application. (In Spring Boot executable jars, `ClassPathResource` skill locations already loaded in 0.12.0; they now also include every JAR, as described above.)
- **`DockerCliExecBackend` on Windows**: commands and environment values containing double quotes reached the container mangled, so commands silently did not run. Arguments are now quoted for the Windows command line.

## New in 0.13.0 (opt-in, no migration required)

- **`SkillsTool.Builder.addSkill(name, description, content)`**: register skills that are not packaged as `SKILL.md` files, for example generated at runtime or loaded from a database. See [SkillsTool](SkillsTool.md#registering-skills-programmatically).
- **`ToolCallListener.afterCompletion(context, toolName, toolInput)`**: a new default method called once after every invocation, whether it succeeded or failed, for per-invocation cleanup. See [ToolCallListener](ToolCallListener.md).
- **`observability-demo` example**: traces tool calls with a `ToolCallListener` and stops running turns with `InterruptAdvisor` (`/stop` or a tool-call budget).

The guides now live only in the docs site sources (`docs/tools`); the duplicate copy in `spring-ai-agent-utils/docs` has been removed.
