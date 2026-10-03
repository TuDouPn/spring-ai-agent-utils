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
package org.springaicommunity.agent.exec.docker;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WindowsCommandLine}. Simulates the Windows round trip on any platform:
 * the JDK's default ("legacy") command-line assembly, then the argument splitting done by
 * {@code docker.exe} (a Go program).
 *
 * @author Christian Tzolov
 */
class WindowsCommandLineTest {

	private static final String PID_WRAPPER = "echo $$ >\"$1\"; shift; exec \"$@\"";

	/** The argv shape built by DockerCliExecBackend#launch. */
	private static List<String> launchArgv(String command) {
		return List.of("docker", "exec", "-w", "/work dir", "-e", "GREETING=say \"hi\"", "cid", "/bin/sh", "-c",
				PID_WRAPPER, "agent-exec", "/tmp/.agent-exec-run_1.pid", "/bin/sh", "-c", command);
	}

	@ParameterizedTest
	@ValueSource(strings = { "echo HELLO", "echo x > /work/proof.txt", "python3 main.py 2>&1; echo \"EXIT=$?\"",
			"printf '%s\\n' \"a \\\"quoted\\\" word\"", "echo \"trailing backslash\\\\\"", "\"\"", "\"",
			"C:\\dir\\ \"C:\\other dir\\\\\"" })
	void quotedArgvSurvivesTheWindowsRoundTrip(String command) {
		List<String> argv = launchArgv(command);

		assertThat(dockerExeArgv(jdkLegacyCommandLine(WindowsCommandLine.quote(argv)))).isEqualTo(argv);
	}

	@Test
	void unquotedArgvLosesItsQuotesOnWindows() {
		// Reproduces #88: without quoting, the wrapper reaches sh with its quotes
		// stripped. The unquoted $@ then re-splits "/bin/sh -c 'echo HELLO'" into
		// "/bin/sh -c echo HELLO", which runs a bare 'echo': exit 0, empty output and
		// no side effects.
		List<String> received = dockerExeArgv(jdkLegacyCommandLine(launchArgv("echo HELLO")));

		assertThat(received).contains("echo $$ >$1; shift; exec $@", "GREETING=say hi")
			.doesNotContain(PID_WRAPPER);
	}

	@Test
	void argumentsWithoutQuotesAreLeftToTheJdk() {
		List<String> argv = List.of("docker", "exec", "-w", "/work dir", "cid", "/bin/sh", "-c", "echo HELLO");

		assertThat(WindowsCommandLine.quote(argv)).isEqualTo(argv);
	}

	@Test
	void quoteArgumentFollowsTheMicrosoftRules() {
		assertThat(WindowsCommandLine.quoteArgument("a\"b")).isEqualTo("\"a\\\"b\"");
		assertThat(WindowsCommandLine.quoteArgument("a\\\"b")).isEqualTo("\"a\\\\\\\"b\"");
		assertThat(WindowsCommandLine.quoteArgument("x\"\\")).isEqualTo("\"x\\\"\\\\\"");
		assertThat(WindowsCommandLine.quoteArgument("a\\b\"")).isEqualTo("\"a\\b\\\"\"");
	}

	/**
	 * The command line the JDK builds in its default legacy mode
	 * ({@code java.lang.ProcessImpl#createCommandLine} with {@code VERIFICATION_LEGACY}):
	 * arguments that are empty or contain whitespace are wrapped in quotes (doubling
	 * trailing backslashes), arguments already wrapped in quotes and all others are
	 * appended as-is; interior quotes are never escaped.
	 */
	private static String jdkLegacyCommandLine(List<String> argv) {
		StringBuilder cmd = new StringBuilder(argv.get(0));
		for (String arg : argv.subList(1, argv.size())) {
			cmd.append(' ');
			boolean alreadyQuoted = arg.length() >= 2 && arg.startsWith("\"") && arg.endsWith("\"");
			boolean needsQuotes = arg.isEmpty() || (!alreadyQuoted && arg.chars().anyMatch(Character::isWhitespace));
			if (needsQuotes) {
				int trailing = 0;
				while (trailing < arg.length() && arg.charAt(arg.length() - 1 - trailing) == '\\') {
					trailing++;
				}
				cmd.append('"').append(arg).append("\\".repeat(trailing)).append('"');
			}
			else {
				cmd.append(arg);
			}
		}
		return cmd.toString();
	}

	/**
	 * Splits a command line the way a Go program such as {@code docker.exe} does on
	 * Windows ({@code os.commandLineToArgv}).
	 */
	private static List<String> dockerExeArgv(String cmd) {
		List<String> args = new ArrayList<>();
		int i = 0;
		while (i < cmd.length()) {
			if (cmd.charAt(i) == ' ' || cmd.charAt(i) == '\t') {
				i++;
				continue;
			}
			StringBuilder arg = new StringBuilder();
			boolean inQuote = false;
			int slashes = 0;
			for (; i < cmd.length(); i++) {
				char c = cmd.charAt(i);
				if ((c == ' ' || c == '\t') && !inQuote) {
					break;
				}
				if (c == '\\') {
					slashes++;
					continue;
				}
				if (c == '"') {
					arg.append("\\".repeat(slashes / 2));
					if (slashes % 2 == 0) {
						if (inQuote && i + 1 < cmd.length() && cmd.charAt(i + 1) == '"') {
							arg.append('"');
							i++;
						}
						inQuote = !inQuote;
					}
					else {
						arg.append('"');
					}
					slashes = 0;
					continue;
				}
				arg.append("\\".repeat(slashes)).append(c);
				slashes = 0;
			}
			args.add(arg.append("\\".repeat(slashes)).toString());
		}
		return args;
	}

}
