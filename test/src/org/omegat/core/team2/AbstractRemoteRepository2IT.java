/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
 with fuzzy matching, translation memory, keyword search,
 glossaries, and translation leveraging into updated projects.

 Copyright (C) 2015 Aaron Madlon-Kay, Alex Buloichik
 Home page: http://www.omegat.org/
 Support center: https://omegat.org/support

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.core.team2;

import gen.core.project.RepositoryDefinition;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public abstract class AbstractRemoteRepository2IT {

	Path tempDir;
	Path tempRepoDir;
	IRemoteRepository2 rr2;
	RepositoryDefinition repositoryDefinition;
	ProjectTeamSettings projectTeamSettings;

	File localCheckoutDir;


	@Before
	public void setUp() throws Exception {

		tempRepoDir = Files.createTempDirectory("omegat-team-repo");
		prepareLocalRepo();

		tempDir = Files.createTempDirectory("omegat-team-it");
		rr2 = getRr2();
		repositoryDefinition = new RepositoryDefinition();
		configureRepositoryDefinition();

		projectTeamSettings = new ProjectTeamSettings(tempDir.toFile());

		localCheckoutDir = new File(this.tempDir.toFile(), "myclonedrepo");
		rr2.init(repositoryDefinition, localCheckoutDir, projectTeamSettings);
		rr2.switchToVersion(null);

		String[] deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals(0, deletedFiles.length);

	}

	abstract void prepareLocalRepo() throws Exception;

	abstract IRemoteRepository2 getRr2();

	abstract void configureRepositoryDefinition();


	@After
	public void tearDown() throws IOException {
		FileUtils.deleteDirectory(tempDir.toFile());
		FileUtils.deleteDirectory(tempRepoDir.toFile());
	}


	@Test
	public void testGetRecentlyDeletedFiles() throws Exception {

		String newFile = createFile(localCheckoutDir);
		String newFile2 = createFile(localCheckoutDir);

		rr2.addForCommit(toRr2Notation(newFile));
		rr2.commit(null, "test add");

		String[] deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals("Add is not a delete", 0, deletedFiles.length);

		rr2.addForDeletion(toRr2Notation(newFile));
		rr2.commit(null, "test delete");
		rr2.addForCommit(toRr2Notation(newFile2));
		rr2.commit(null, "test add2");

		deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals("In list of commits, the delete is found", 1, deletedFiles.length);
		assertEquals("In list of commits, the delete is found", newFile, deletedFiles[0]);

		deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals("calling method second time gives empty list", 0, deletedFiles.length);

		String newFile3 = createFile(localCheckoutDir);
		rr2.addForCommit(toRr2Notation(newFile3));
		rr2.commit(null, "test add");
		deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals("Add is not a delete", 0, deletedFiles.length);

		rr2.addForDeletion(toRr2Notation(newFile3));
		rr2.commit(null, "test delete");

		FileWriter myWriter = new FileWriter(new File(localCheckoutDir, newFile3));
		myWriter.write("Files in Java might be tricky, but it is fun enough!");
		myWriter.close();
		rr2.addForCommit(toRr2Notation(newFile3));
		rr2.commit(null, "test add deleted file");

		deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals("delete not in list if added later", 0, deletedFiles.length);

		testDelSubdir();
		testDelSubfile();
	}

	@NotNull
	protected String createFile(File basedir) throws IOException {
		Random random = new Random();
		String newFile = "file"+random.nextInt();
		FileWriter myWriter = new FileWriter(new File(basedir, newFile));
		myWriter.write("Files in Java might be tricky, but it is fun enough!");
		myWriter.close();
		return newFile;
	}

	void testDelSubfile() throws Exception {
		String subdir = "subdir2";
		String fileToDelete = createFileInSubdir(localCheckoutDir, subdir);

		rr2.addForCommit(toRr2Notation(fileToDelete));
		rr2.commit(null, "add so we can delete");
		String[] deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals(0, deletedFiles.length);

		rr2.addForDeletion(toRr2Notation(fileToDelete));
		rr2.commit(null, "test delete file");

		deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals(1, deletedFiles.length);
		assertEquals("the file is deleted", fileToDelete, deletedFiles[0]);
	}

	void testDelSubdir() throws Exception {
		String dirToDelete = "subdir";
		String newFile2 = createFileInSubdir(localCheckoutDir, dirToDelete);

		rr2.addForCommit(toRr2Notation(newFile2));
		rr2.commit(null, "add so we can delete");
		String[] deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals(0, deletedFiles.length);

		rr2.addForDeletion(toRr2Notation(dirToDelete));
		rr2.commit(null, "test delete dir");
		deletedFiles = rr2.getRecentlyDeletedFiles();
		assertEquals(1, deletedFiles.length);
		assertFileOrDirDeleted(dirToDelete, newFile2, deletedFiles[0]);
	}

	@NotNull
	protected String createFileInSubdir(File basedir, String subdir) throws IOException {
		String newFile = subdir + File.separator + "fileinsubdir";
		Path path = Paths.get(basedir.getAbsolutePath() + File.separator + subdir);
		Files.createDirectories(path);
		FileWriter myWriter2 = new FileWriter(new File(basedir, newFile));
		myWriter2.write("This file is in a dir");
		myWriter2.close();
		return newFile;
	}

	abstract String toRr2Notation(String file);

	abstract void assertFileOrDirDeleted(String dir, String fileInDir, String actual);
}
