package io.github.fvarrui.javapackager.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.zip.UnixStat;
import org.vafer.jdeb.Console;
import org.vafer.jdeb.DataProducer;
import org.vafer.jdeb.DebMaker;
import org.vafer.jdeb.ant.Data;
import org.vafer.jdeb.ant.Mapper;
import org.vafer.jdeb.mapping.PermMapper;
import org.vafer.jdeb.producers.DataProducerLink;

import io.github.fvarrui.javapackager.packagers.ArtifactGenerator;
import io.github.fvarrui.javapackager.packagers.LinuxPackager;
import io.github.fvarrui.javapackager.packagers.Packager;
import io.github.fvarrui.javapackager.utils.Logger;
import io.github.fvarrui.javapackager.utils.VelocityUtils;

/**
 * Creates a DEB package file including all app folder's content only for 
 * GNU/Linux so app could be easily distributed on Gradle context
 */
public class GenerateDeb extends ArtifactGenerator {

	private Console console;
	
	public GenerateDeb() {
		super("DEB package");
		console = new Console() {
			
			@Override
			public void warn(String message) {
				Logger.warn(message);
			}
			
			@Override
			public void info(String message) {
				Logger.info(message);
			}
			
			@Override
			public void debug(String message) {
				Logger.debug(message);
			}
			
		};
	}
	
	@Override
	public boolean skip(Packager packager) {
		return !packager.getLinuxConfig().isGenerateDeb();
	}
	
	@Override
	protected File doApply(Packager packager) throws Exception {
		
		LinuxPackager linuxPackager = (LinuxPackager) packager;
		
		File assetsFolder = linuxPackager.getAssetsFolder();
		String name = linuxPackager.getName();
		File appFolder = linuxPackager.getAppFolder();
		File outputDirectory = linuxPackager.getOutputDirectory();
		String version = linuxPackager.getVersion();
		boolean bundleJre = linuxPackager.getBundleJre();
		String jreDirectoryName = linuxPackager.getJreDirectoryName();
		File executable = linuxPackager.getExecutable();
		File javaFile = new File(appFolder, jreDirectoryName + "/bin/java");

		// generates desktop file from velocity template
		File desktopFile = new File(assetsFolder, name + ".desktop");
		VelocityUtils.render("linux/desktop.vtl", desktopFile, linuxPackager);
		Logger.info("Desktop file rendered in " + desktopFile.getAbsolutePath());
		
		// generates deb control file from velocity template
		File controlFile = new File(assetsFolder, "control");
		VelocityUtils.render("linux/control.vtl", controlFile, linuxPackager);
		Logger.info("Control file rendered in " + controlFile.getAbsolutePath());

		// generated deb file
		File debFile = new File(outputDirectory, name + "_" + version + ".deb");

		// create data producers collections
		
		List<DataProducer> conffilesProducers = new ArrayList<>();		
		List<DataProducer> dataProducers = new ArrayList<>();		
		
		// builds app folder data producer, except executable file and jre/bin/java
		
		Mapper appFolderMapper = new Mapper();
		appFolderMapper.setType("perm");
		appFolderMapper.setPrefix("/opt/" + name);
		
		Data appFolderData = new Data();
		appFolderData.setType("directory");
		appFolderData.setSrc(appFolder);
		appFolderData.setExcludes(executable.getName() + (bundleJre ? "," + jreDirectoryName + "/bin/java" : ""));
		appFolderData.addMapper(appFolderMapper);

		dataProducers.add(appFolderData);

		// builds executable data producer
		
		Mapper executableMapper = new Mapper();
		appFolderMapper.setType("perm");
		appFolderMapper.setPrefix("/opt/" + name);
		appFolderMapper.setFileMode("755");
		
		Data executableData = new Data();
		executableData.setType("file");
		executableData.setSrc(executable);
		executableData.addMapper(executableMapper);

		dataProducers.add(executableData);

		// desktop file data producer 

		Mapper desktopFileMapper = new Mapper();
		desktopFileMapper.setType("perm");
		desktopFileMapper.setPrefix("/usr/share/applications");
		
		Data desktopFileData = new Data();
		desktopFileData.setType("file");
		desktopFileData.setSrc(desktopFile);
		desktopFileData.addMapper(desktopFileMapper);
		
		dataProducers.add(desktopFileData);

		// java binary file data producer
		
		if (bundleJre) {
			
			Mapper javaBinaryMapper = new Mapper();
			javaBinaryMapper.setType("perm");
			javaBinaryMapper.setFileMode("755");
			javaBinaryMapper.setPrefix("/opt/" + name + "/" + jreDirectoryName + "/bin");
			
			Data javaBinaryData = new Data();
			javaBinaryData.setType("file");
			javaBinaryData.setSrc(javaFile);
			javaBinaryData.addMapper(javaBinaryMapper);

			dataProducers.add(javaBinaryData);
			
		}
		
		// symbolic link in /usr/local/bin to app binary data producer

		int linkMode = UnixStat.LINK_FLAG | Integer.parseInt("777", 8);
		
		org.vafer.jdeb.mapping.Mapper linkMapper = new PermMapper(
				0, 0, 					// uid, gid 
				"root", "root", 		// user, group
				linkMode, linkMode, 	// perms 
				0, null
			);
		
        DataProducer linkData = new DataProducerLink(
        		"/usr/local/bin/" + name,		// link name 
        		"/opt/" + name + "/" + name, 	// target
        		true, 							// symbolic link
        		null, null, 
        		new org.vafer.jdeb.mapping.Mapper[] { linkMapper }	// link mapper
    		);

		dataProducers.add(linkData);
		
		// builds deb file
		
        DebMaker debMaker = new DebMaker(console, dataProducers, conffilesProducers);
        debMaker.setDeb(debFile);
        debMaker.setControl(controlFile.getParentFile());
        debMaker.setCompression("gzip");
        debMaker.setDigest("SHA256");
        debMaker.validate();
        debMaker.makeDeb();

		return debFile;

	}
	
}
