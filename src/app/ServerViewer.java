package app;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ServerViewer {

	private JSONParser parser;
	private String repoListUrl;
	private String repoBranch;
	private String repoBranchUrl;
	private JSONArray packages;
	private Scanner input;

	private ServerViewer() {
		parser = new JSONParser();
		repoListUrl = "https://martinmimigames.github.io/repo_list.json";
		repoBranch = "stable";
		input = new Scanner(System.in);
	}

	private ArrayList<String> getRepoList() throws Exception {
		System.out.print("Getting repo list...");
		JSONObject json = (JSONObject) parser.parse(getWebContent(repoListUrl));
		int version = Integer.parseInt((String) json.get("version"));
		ArrayList<String> repos = new ArrayList<>(1);
		switch (version) {
		case 1:
			JSONArray repoList = (JSONArray) json.get("repo_list");

			for (int i = 0; i < repoList.size(); i++) {
				repos.add((String) repoList.get(i));
			}
		}
		System.out.println(" Done!");
		return repos;
	}

	private String getRepoUrl() throws Exception {
		ArrayList<String> repoList = getRepoList();
		System.out.print("Choosing repo...");
		String repoUrl = repoList.get(0);
		System.out.println(" Done!");
		return repoUrl;
	}

	private JSONObject getRepo(String repoUrl) throws Exception {
		System.out.print("Getting repo info...");
		JSONObject repo = (JSONObject) parser.parse(getWebContent(repoUrl));
		System.out.println(" Done!");
		System.out.println();
		System.out.println("Using repo: " + repo.get("name"));
		System.out.println("Description: " + repo.get("description"));
		System.out.println("Maintainer: " + repo.get("maintainer"));
		System.out.println();
		return repo;
	}

	private void setRepoBranchUrl() throws Exception {
		String repoUrl = getRepoUrl();
		JSONObject repo = getRepo(repoUrl);
		System.out.println("Using branch: " + repoBranch);
		repoBranchUrl = (String) ((JSONObject) repo.get("release_type")).get(repoBranch);
		repoBranchUrl = getAbsolutePath(repoUrl, repoBranchUrl);
	}

	private void setPackages() throws Exception {
		setRepoBranchUrl();
		System.out.print("Getting package list...");
		JSONObject packageList = (JSONObject) parser.parse(getWebContent(repoBranchUrl));
		packages = (JSONArray) packageList.get("packages");
		System.out.println(" Done!");
	}

	private void listPackages() {
		System.out.println("Listing packages...");
		for (int i = 0; i < packages.size(); i++) {
			JSONObject packageEntry = (JSONObject) packages.get(i);
			System.out.println("\t" + (i + 1) + ". " + packageEntry.get("name"));
		}
	}

	private JSONObject findPackage(String name) {
		for (int i = 0; i < packages.size(); i++) {
			JSONObject packageEntry = (JSONObject) packages.get(i);
			if (packageEntry.get("name").equals(name))
				return packageEntry;
		}
		return null;
	}

	private String getPackageUrl(String packageName) {
		JSONObject packageEntry = findPackage(packageName);
		String packageUrl = (String) packageEntry.get("url");
		if (packageUrl == null) {
			System.out.println("package not found");
			return null;
		}
		packageUrl = (String) getAbsolutePath(repoBranchUrl, packageUrl);
		return packageUrl;
	}

	private String getDownloadUrl(String packageUrl, JSONObject release) throws Exception {
		String downloadsUrl = (String) release.get("url");
		downloadsUrl = getAbsolutePath(packageUrl, downloadsUrl);
		JSONArray downloads = (JSONArray) ((JSONObject) parser.parse(getWebContent(downloadsUrl))).get("downloads");
		for (int t = 0; t < downloads.size(); t++) {
			JSONObject download = (JSONObject) downloads.get(t);
			String platform = (String) download.get("platform");
			if (platform.equals("*"))
				platform = "windows";
			String arch = (String) download.get("arch");
			if (arch.equals("*"))
				arch = "x86_64";
			if (platform.equals("windows") && arch.equals("x86_64")) {
				String downloadUrl = (String) download.get("file");
				downloadUrl = getAbsolutePath(downloadsUrl, downloadUrl);
				return downloadUrl;
			}
		}
		return null;
	}

	private void downloadPackage(String packageName) throws Exception {
		String packageUrl = getPackageUrl(packageName);
		if (packageUrl == null) {
			System.out.println("package not found");
			return;
		}
		System.out.print("Finding suitable release...");
		JSONObject releases = (JSONObject) parser.parse(getWebContent(packageUrl));
		JSONObject latestRelease = (JSONObject) releases.get("latest");
		String downloadUrl = getDownloadUrl(packageUrl, latestRelease);
		if (downloadUrl == null) {
			System.out.println("no suitable release");
			return;
		}
		System.out.println(" Done!");
		
		System.out.println();
		String downloadFolder = System.getProperty("user.home") + "\\Downloads";
		System.out.println("Download folder: " + downloadFolder);
		File targetFile = new File(downloadFolder + downloadUrl.substring(downloadUrl.lastIndexOf('/'), downloadUrl.length()));
		if (targetFile.isDirectory() || targetFile.isFile()) {
			System.out.println(targetFile.getPath() + " already existed");
			System.out.println("Do you want to replace it? [Y/n]");
			System.out.print("> ");

			String answer = input.next();
			if (!answer.toUpperCase().equals("Y")) {
				System.out.println("Aborted!");
				return;
			}
		}
		System.out.print("Downloading...");
		targetFile.createNewFile();
		Files.copy(new URL(downloadUrl).openStream(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		System.out.println(" Done!");
	}

	public void run() throws Exception {
		setPackages();
		System.out.println();
		listPackages();
		System.out.println();
		System.out.println("Enter package to download");
		System.out.print("> ");
		String packageName = input.next();
		downloadPackage(packageName);
		input.close();
	}

	private String getWebContent(String url) throws IOException {
		Scanner sc = new Scanner(new URL(url).openStream());
		String text = sc.useDelimiter("\\A").next();
		sc.close();
		return text;
	}

	private String getAbsolutePath(String baseUrl, String relativeUrl) {
		try {
			new URL(relativeUrl);
		} catch (MalformedURLException e) {
			return baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + relativeUrl;
		}
		return relativeUrl;
	}

	public static void main(String args[]) throws Exception {
		ServerViewer sv = new ServerViewer();
		sv.run();
	}
}