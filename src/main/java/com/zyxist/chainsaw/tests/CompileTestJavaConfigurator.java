/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zyxist.chainsaw.tests;

import com.zyxist.chainsaw.JavaModule;
import com.zyxist.chainsaw.TaskConfigurator;
import com.zyxist.chainsaw.algorithms.ModulePatcher;
import com.zyxist.chainsaw.jigsaw.JigsawCLI;
import com.zyxist.chainsaw.jigsaw.cli.PatchItem;
import com.zyxist.chainsaw.jigsaw.cli.ReadItem;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Objects;

import static com.zyxist.chainsaw.ChainsawPlugin.PATCH_CONFIGURATION_NAME;

public class CompileTestJavaConfigurator implements TaskConfigurator<JavaCompile> {
	private final JavaModule moduleConfig;
	private final TestEngine testEngine;

	public CompileTestJavaConfigurator(JavaModule moduleConfig, TestEngine testEngine) {
		this.moduleConfig = Objects.requireNonNull(moduleConfig);
		this.testEngine = Objects.requireNonNull(testEngine);
	}

	@Override
	public void updateConfiguration(Project project, JavaCompile compileJava) {
		compileJava.getInputs().property("moduleName", moduleConfig.getName());
		JigsawCLI cli = new JigsawCLI(compileJava.getClasspath().getAsPath());
		ModulePatcher patcher = new ModulePatcher(moduleConfig.getHacks().getPatchedDependencies());
		final SourceSet test = ((SourceSetContainer) project.getProperties().get("sourceSets")).getByName("test");

		cli.addModules()
			.addAll(testEngine.getTestEngineModules())
			.addAll(moduleConfig.getExtraTestModules());
		cli.readList()
			.read(new ReadItem(moduleConfig.getName())
				.toAll(testEngine.getTestEngineModules())
				.toAll(moduleConfig.getExtraTestModules()));
		patcher
			.patchFrom(project, PATCH_CONFIGURATION_NAME)
			.forEach((k, patchedModule) -> cli.patchList().patch(patchedModule));
		cli.patchList().patch(
			new PatchItem(moduleConfig.getName())
				.with(test.getJava().getSourceDirectories().getAsPath())
		);

		compileJava.getOptions().getCompilerArgs().addAll(cli.generateArgs());
		compileJava.setClasspath(project.files());
	}
}
