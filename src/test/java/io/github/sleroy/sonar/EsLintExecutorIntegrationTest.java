package io.github.sleroy.sonar;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.Before;

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.TempFolder;

import io.github.sleroy.sonar.api.EsLintExecutor;
import io.github.sleroy.sonar.api.EsLintParser;
import io.github.sleroy.sonar.api.PathResolver;
import io.github.sleroy.sonar.model.EsLintIssue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Integration test for EsLint
 */
@Category(IntegrationTest.class)
@RunWith(value = MockitoJUnitRunner.class)
public class EsLintExecutorIntegrationTest {
    Configuration settings;

    DefaultInputFile file;
    DefaultInputFile typeDefFile;

    EsLintExecutor executor;
    EsLintParser   parser;
    EsLintSensor   sensor;

    SensorContextTester context;

    PathResolver	    resolver;
    HashMap<String, String> fakePathResolutions;

    @Mock
    System2 system;

    @Mock
    TempFolder tempFolder;

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

    @Ignore("ESLint Integration debug test")
    @Test
    public void testEsLint() throws IOException {
        EsLintExecutorConfig esLintConfiguration = new EsLintExecutorConfig();
        esLintConfiguration.setConfigFile("src\\test\\resources\\.eslintrc.js");
        esLintConfiguration.setPathToEsLint("C:\\Users\\Administrator\\AppData\\Roaming\\npm\\node_modules\\eslint\\bin\\eslint.js");
        esLintConfiguration.setTimeoutMs(40000);

        when(tempFolder.newFile()).thenReturn(File.createTempFile("eslintexecutor", ".json"));

        EsLintExecutor esLintExecutor = new EsLintExecutorImpl(system, tempFolder);

        List<String> files = new ArrayList<>();
        files.add(new File("src/test/resources/dashboard.js").getAbsolutePath());

        List<String> commandOutput = esLintExecutor.execute(esLintConfiguration, files, context);
        assertNotNull(commandOutput);
        assertEquals("Expected number of results", 1, commandOutput.size());
    }
}
