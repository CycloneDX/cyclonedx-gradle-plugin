package org.cyclonedx.gradle;

/**
 * Convert a Maven SCM URL into a valid HTTPS URL.
 *
 * Background:
 * The Maven XML schema doesn't restrict the SCM URL to be a valid URL but the CycloneDX schema requires a valid URL.
 * Some popular Java libraries use sometimes the connectionString or the SSH Git URL in the Maven SCM URL element.
 * Means instead of losing the SCM URL we simply translate the value to an HTTPS URL.
 */
public class ScmUrlConverter {
	private static String GIT_SCHEMA = "git://";
	private static String GIT_SSH = "git@";
	private static String HTTPS_SCHEMA = "https://";
	private static String SCM_PREFIX = "scm:git:";

	/**
	 * Convert Maven SCM URL to HTTPS URL.
	 * Mapping:
	 * - scm:git:git@github.com:user/repo.git -> https://github.com/user/repo.git
	 * - scm:git:git://github.com/user/repo.git -> https://github.com/user/repo.git
	 * - git@github.com:user/repo.git -> https://github.com/user/repo.git
	 *
	 * @param link original scm url
	 * @return HTTPS URI string
	 */
	public String convert(final String link) {
		if(link.startsWith(SCM_PREFIX)) {
			return handleScmGit(link);
		} else if(link.startsWith(GIT_SSH)) {
			return handleGit(link);
		}
		else {
			return link.trim();
		}
	}

	/**
	 * Convert SCM URL with prefix 'scm:git:'
	 * @param link original scm url
	 * @return HTTPS URI string
	 */
	private String handleScmGit(String link) {
		return handleGit(link.substring(SCM_PREFIX.length()));
	}

	/**
	 * Convert SCN URL which starts with: 'git@' or 'git://'.
	 * @param link original scm url
	 * @return HTTPS URI string
	 */
	private String handleGit(String link) {
		if(link.startsWith(GIT_SSH)) {
			link = HTTPS_SCHEMA + link.substring(GIT_SSH.length()).replace(":", "/");
		} else if (link.startsWith(GIT_SCHEMA)) {
			link = HTTPS_SCHEMA + link.substring(GIT_SCHEMA.length());
		}

		return link;
	}
}
