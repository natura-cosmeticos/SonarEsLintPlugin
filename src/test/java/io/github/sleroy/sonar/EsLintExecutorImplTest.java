/*
 * Copyright (C) 2017 Sylvain Leroy - BYOSkill Company All Rights Reserved
 * You may use, distribute and modify this code under the
 * terms of the MIT license, which unfortunately won't be
 * written for another century.
 *
 * You should have received a copy of the MIT license with
 * this file. If not, please write to: sleroy at byoskill.com, or visit : www.byoskill.com
 * 
 */
package io.github.sleroy.sonar;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import org.mockito.stubbing.Answer;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.Configuration;
import org.sonar.api.internal.apachecommons.lang.SystemUtils;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;

import io.github.sleroy.sonar.api.EsLintExecutor;
import io.github.sleroy.sonar.api.EsLintParser;
import io.github.sleroy.sonar.api.PathResolver;
import io.github.sleroy.sonar.model.EsLintIssue;

public class EsLintExecutorImplTest {
    EsLintExecutorImpl executorImpl;
    CommandExecutor    commandExecutor;
    TempFolder	       tempFolder;
    File	       tempOutputFile;

    System2 system;

    EsLintExecutorConfig config;

    Configuration settings;

    DefaultInputFile file;
    DefaultInputFile typeDefFile;

    EsLintExecutor executor;
    EsLintParser   parser;
    EsLintSensor   sensor;

    SensorContextTester context;

    PathResolver	    resolver;
    HashMap<String, String> fakePathResolutions;

    @Before
    public void before() throws Exception {
        fakePathResolutions = new HashMap<>();
        fakePathResolutions.put(EsLintPlugin.SETTING_ES_LINT_PATH, "/path/to/eslint");
        fakePathResolutions.put(EsLintPlugin.SETTING_ES_LINT_CONFIG_PATH, "src/test/resources/.eslintrc.js");
        fakePathResolutions.put(EsLintPlugin.SETTING_ES_LINT_RULES_DIR, "/path/to/rules");

        settings = mock(Configuration.class);
        when(settings.getInt(EsLintPlugin.SETTING_ES_LINT_TIMEOUT)).thenReturn(Optional.of(45000));
        when(settings.getBoolean(EsLintPlugin.SETTING_ES_LINT_ENABLED)).thenReturn(Optional.of(true));
        executor = mock(EsLintExecutor.class);
        parser = mock(EsLintParser.class);

        resolver = mock(PathResolver.class);
        sensor = spy(new EsLintSensor(settings, resolver, executor, parser));

        file = TestInputFileBuilder
            .create("", "path/to/file").setLanguage(EsLintLanguage.LANGUAGE_KEY).setLines(1).setLastValidOffset(999)
            .setOriginalLineOffsets(new int[] { 5 }).build();

        typeDefFile = TestInputFileBuilder
            .create("", "path/to/file.d.ts").setLanguage(EsLintLanguage.LANGUAGE_KEY).setLines(1)
            .setLastValidOffset(999)
            .setOriginalLineOffsets(new int[] { 5 }).build();

        context = SensorContextTester.create(new File(""));
        context.fileSystem().add(file);
        context.fileSystem().add(typeDefFile);
    }

    @Test
    public void BatchesExecutions_IfTooManyFilesForCommandLine() {
	final List<String> filenames = new ArrayList<>();
	int currentLength = 0;
	final int standardCmdLength = "node path/to/eslint --rules-dir path/to/rules --out path/to/temp --config path/to/config"
		.length();

	final String firstBatch = "first batch";
	while (currentLength + 12 < EsLintExecutorImpl.MAX_COMMAND_LENGTH - standardCmdLength) {
	    filenames.add(firstBatch);
	    currentLength += firstBatch.length() + 1; // 1 for the space
	}
	filenames.add("second batch");

	final ArrayList<Command> capturedCommands = new ArrayList<>();
	final ArrayList<Long> capturedTimeouts = new ArrayList<>();

	final Answer<Integer> captureCommand = invocation -> {
	    capturedCommands.add((Command) invocation.getArguments()[0]);
	    capturedTimeouts.add((long) invocation.getArguments()[3]);
	    return 0;
	};

	when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class),
		any(long.class))).then(captureCommand);
	executorImpl.execute(config, filenames, context);

	assertEquals(2, capturedCommands.size());

    }

    @Test
    public void DoesNotAddRulesDirParameter_IfEmptyString() {
	final ArrayList<Command> capturedCommands = new ArrayList<>();
	final ArrayList<Long> capturedTimeouts = new ArrayList<>();

	final Answer<Integer> captureCommand = invocation -> {
	    capturedCommands.add((Command) invocation.getArguments()[0]);
	    capturedTimeouts.add((long) invocation.getArguments()[3]);
	    return 0;
	};

	when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class),
		any(long.class))).then(captureCommand);

	config.setRulesDir("");
	executorImpl.execute(config, Arrays.asList(new String[] { "path/to/file" }), context);

	final Command theCommand = capturedCommands.get(0);
	assertFalse(theCommand.toCommandLine().contains("--rules-dir"));
    }

    @Test
    public void DoesNotAddRulesDirParameter_IfNull() {
	final ArrayList<Command> capturedCommands = new ArrayList<>();
	final ArrayList<Long> capturedTimeouts = new ArrayList<>();

	final Answer<Integer> captureCommand = invocation -> {
	    capturedCommands.add((Command) invocation.getArguments()[0]);
	    capturedTimeouts.add((long) invocation.getArguments()[3]);
	    return 0;
	};

	when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class),
		any(long.class))).then(captureCommand);

	config.setRulesDir(null);
	executorImpl.execute(config, Arrays.asList(new String[] { "path/to/file" }), context);

	final Command theCommand = capturedCommands.get(0);
	assertFalse(theCommand.toCommandLine().contains("--rules-dir"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void execute_throws_ifNullConfigSupplied() {
        executorImpl.execute(null, new ArrayList<String>(), context);
    }

    @Test(expected = IllegalArgumentException.class)
    public void execute_throws_ifNullFileListSupplied() {
        executorImpl.execute(config, null, context);
    }

    @Test
    public void executesCommandWithCorrectArgumentsAndTimeouts() {
        final ArrayList<Command> capturedCommands = new ArrayList<>();
        final ArrayList<Long> capturedTimeouts = new ArrayList<>();

        final Answer<Integer> captureCommand = invocation -> {
            capturedCommands.add((Command) invocation.getArguments()[0]);
            capturedTimeouts.add((long) invocation.getArguments()[3]);
            return 0;
        };

        when(commandExecutor.execute(any(Command.class), any(StreamConsumer.class), any(StreamConsumer.class),
                                     any(long.class))).then(captureCommand);
        executorImpl.execute(config, Arrays.asList(new String[] { "path/to/file", "path/to/another" }), context);

        assertEquals(1, capturedCommands.size());

        final Command theCommand = capturedCommands.get(0);
        final long theTimeout = capturedTimeouts.get(0);

        assertEquals(
                     "node path/to/eslint -f json --rules-dir path/to/rules --output-file path/to/temp --config path/to/config path/to/file path/to/another",
                     theCommand.toCommandLine()
                     );
        // Expect one timeout period per file processed
        assertEquals(2 * 40000, theTimeout);
    }

    @Before
    public void setUp() throws Exception {
	system = mock(System2.class);
	when(system.isOsWindows()).thenReturn(SystemUtils.IS_OS_WINDOWS);

	tempFolder = mock(TempFolder.class);

	tempOutputFile = mock(File.class);
	when(tempOutputFile.getAbsolutePath()).thenReturn("path/to/temp");
	when(tempFolder.newFile()).thenReturn(tempOutputFile);

	commandExecutor = mock(CommandExecutor.class);

	executorImpl = spy(new EsLintExecutorImpl(system, tempFolder));
	when(executorImpl.createExecutor()).thenReturn(commandExecutor);
	doReturn(mock(BufferedReader.class)).when(executorImpl).getBufferedReaderForFile(any(File.class));

	// Setup a default config, which each method will mutate as required
	config = new EsLintExecutorConfig();
	config.setPathToEsLint("path/to/eslint");
	config.setConfigFile("path/to/config");
	config.setRulesDir("path/to/rules");
	config.setTimeoutMs(40000);
    }
}
