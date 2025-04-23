package tragdor.util;

public class Exit {
	public static void exit(int exitCode) {
		System.out.println("Exiting, code:" + exitCode);
		System.exit(exitCode);
	}
}
