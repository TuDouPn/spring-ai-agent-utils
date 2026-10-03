package org.springaicommunity.agent;

import org.springaicommunity.agent.tools.ToolCallListener;

/**
 * Prints one line per tool invocation to the console: the tool name, its (abbreviated)
 * input, the duration and whether it succeeded. Tool failures are reported back to the
 * model as the tool result, so the agent can adapt instead of the turn failing.
 */
class ConsoleToolCallListener implements ToolCallListener {

	private static final int MAX_INPUT_LENGTH = 100;

	@Override
	public Object beforeCall(String toolName, String toolInput) {
		System.out.println("  > " + toolName + " " + abbreviate(toolInput));
		// Correlation context handed back to afterCall/onError: the start time
		return System.nanoTime();
	}

	@Override
	public void afterCall(Object context, String toolName, String toolInput, String result) {
		System.out.println("  < " + toolName + " ok (" + elapsedMillis(context) + " ms)");
	}

	@Override
	public String onError(Object context, String toolName, String toolInput, RuntimeException ex) {
		System.out.println("  < " + toolName + " FAILED (" + elapsedMillis(context) + " ms): " + ex.getMessage());
		// Report the failure to the model as the tool result; returning null would rethrow
		return "Tool '" + toolName + "' failed: " + ex.getMessage();
	}

	private static long elapsedMillis(Object startNanos) {
		return (System.nanoTime() - (long) startNanos) / 1_000_000;
	}

	private static String abbreviate(String text) {
		String oneLine = text.replaceAll("\\s+", " ");
		return (oneLine.length() <= MAX_INPUT_LENGTH) ? oneLine : oneLine.substring(0, MAX_INPUT_LENGTH) + "...";
	}

}
