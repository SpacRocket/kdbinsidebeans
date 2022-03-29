package org.kdb.inside.brains.lang.formatting;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.*;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kdb.inside.brains.QLanguage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.WrappingOrBraceOption;
import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions.getInstance;

public class QCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
    @Override
    public @NotNull Language getLanguage() {
        return QLanguage.INSTANCE;
    }

    @Override
    public @Nullable String getCodeSample(@NotNull SettingsType settingsType) {
        InputStream s = getClass().getResourceAsStream("/org/kdb/inside/brains/codeStyle/" + settingsType.name().toLowerCase() + ".txt");
        if (s == null) {
            s = getClass().getResourceAsStream("/org/kdb/inside/brains/codeStyle/default.txt");
        }

        if (s == null) {
            return null;
        }

        try {
            return IOUtils.toString(s, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public IndentOptionsEditor getIndentOptionsEditor() {
        return new SmartIndentOptionsEditor();
    }

    @Override
    public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
        if (settingsType == SettingsType.SPACING_SETTINGS) {
            customizeSpacing(consumer);
        } else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
            customizeWrapping(consumer);
/*
        } else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
            consumer.showAllStandardOptions();
*/
        }
    }

    @Override
    public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
        return new QCodeStyleSettings(settings);
    }

    private void customizeWrapping(@NotNull CodeStyleSettingsCustomizable consumer) {
        consumer.showStandardOptions(
                names(
                        WrappingOrBraceOption.RIGHT_MARGIN,
                        WrappingOrBraceOption.WRAP_ON_TYPING,
                        WrappingOrBraceOption.KEEP_LINE_BREAKS
                )
        );

        final String control = "Control statement (if, do, while, ...)";
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_WRAP_TYPE", control, null, getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_ALIGN_EXPRS", "Align when multiline", control);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_ALIGN_BRACKET", "Align bracket when multiline", control);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_LBRACKET_ON_NEXT_LINE", "New line after '['", control);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_RBRACKET_ON_NEXT_LINE", "Place ']' on new line", control);

        final String condition = "Condition statement (?[], $[], @[], .[], ![], ...)";
        consumer.showCustomOption(QCodeStyleSettings.class, "CONDITION_WRAP_TYPE", condition, null, getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONDITION_ALIGN_EXPRS", "Align when multiline", condition);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONDITION_ALIGN_BRACKET", "Align bracket when multiline", condition);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONDITION_LBRACKET_ON_NEXT_LINE", "New line after '['", condition);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONDITION_RBRACKET_ON_NEXT_LINE", "Place ']' on new line", condition);

        final String lambda = "Lambda definition";
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_ALIGN_BRACE", "Align braces", lambda);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_GLOBAL_SPACE_BEFORE_CLOSE_BRACE", "Space before global close brace", lambda);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_PARAMS_WRAP", "Wrap parameters", lambda, getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_PARAMS_ALIGN_NAMES", "Align parameters when multiline", lambda);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_PARAMS_ALIGN_BRACKETS", "Align parameter brackets when multiline", lambda);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_PARAMS_LBRACKET_ON_NEXT_LINE", "New line after '['", lambda);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_PARAMS_RBRACKET_ON_NEXT_LINE", "Place ']' on new line", lambda);

        final String mode = "Mode (k), M), ...)";
        consumer.showCustomOption(QCodeStyleSettings.class, "MODE_WRAP_TYPE", mode, null, getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);
        consumer.showCustomOption(QCodeStyleSettings.class, "MODE_ALIGN", "Align when multiline", mode);
    }

    private void customizeSpacing(@NotNull CodeStyleSettingsCustomizable consumer) {
        final String operators = "Around operators";
        consumer.showCustomOption(QCodeStyleSettings.class, "SPACE_AROUND_ASSIGNMENT_OPERATORS", "Assignment operators (::, :, ...)", operators);

        consumer.showCustomOption(QCodeStyleSettings.class, "SPACE_AROUND_OPERATOR_ARITHMETIC", "Arithmetic operators (+, -, * , %)", operators);
        consumer.showCustomOption(QCodeStyleSettings.class, "SPACE_AROUND_OPERATOR_ORDER", "Order operators (<= , >= , < , >)", operators);
        consumer.showCustomOption(QCodeStyleSettings.class, "SPACE_AROUND_OPERATOR_EQUALITY", "Equality operators (~ , = , <>)", operators);
        consumer.showCustomOption(QCodeStyleSettings.class, "SPACE_AROUND_OPERATOR_WEIGHT", "Weight operators (&, |)", operators);
        consumer.showCustomOption(QCodeStyleSettings.class, "SPACE_AROUND_OPERATOR_OTHERS", "Mixed operators (!, #, @, _ , ? , ^, $)", operators);

        // Lambda definition
        final String lambda = "Lambda definition";
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_SPACE_WITHIN_BRACES", "Within braces", lambda);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_SPACE_AFTER_PARAMETERS", "After parameters", lambda);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_SPACE_WITHIN_PARAMS_BRACKETS", "Within parameter brackets", lambda);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_SPACE_AFTER_PARAMS_SEMICOLON", "After parameter semicolon", lambda);
        consumer.showCustomOption(QCodeStyleSettings.class, "LAMBDA_SPACE_BEFORE_PARAMS_SEMICOLON", "Before parameter semicolon", lambda);

        // Control settings
        final String control = "Control statement (if, do, while, ...)";
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_SPACE_AFTER_OPERATOR", "After operator", control);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_SPACE_WITHIN_BRACES", "Within brackets", control);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_SPACE_AFTER_SEMICOLON", "After semicolon", control);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_SPACE_BEFORE_SEMICOLON", "Before semicolon", control);

        // Condition settings
        final String condition = "Condition statement ($, @, ?, ...)";
        consumer.showCustomOption(QCodeStyleSettings.class, "CONDITION_SPACE_AFTER_OPERATOR", "After operator", condition);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONDITION_SPACE_WITHIN_BRACES", "Within brackets", condition);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONDITION_SPACE_AFTER_SEMICOLON", "After semicolon", condition);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONDITION_SPACE_BEFORE_SEMICOLON", "Before semicolon", condition);

        // Mode
        final String mode = "Mode (k), M), ...)";
        consumer.showCustomOption(QCodeStyleSettings.class, "MODE_SPACE_AFTER", "After mode name", mode);

        // Commands
        final String commands = "Commands";
        consumer.showCustomOption(QCodeStyleSettings.class, "IMPORT_TRIM_TAIL", "Trim spaces after import command", commands);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTEXT_TRIM_TAIL", "Trim spaces after context command", commands);

        final String other = "Other";
        consumer.showCustomOption(QCodeStyleSettings.class, "RETURN_SPACE_AFTER_COLON", "After return colon", other);
        consumer.showCustomOption(QCodeStyleSettings.class, "SIGNAL_SPACE_AFTER_SIGNAL", "After signal apostrophe", other);
        consumer.showCustomOption(QCodeStyleSettings.class, "CONTROL_SPACE_BEFORE_EXECUTION", "Before execution statement (.)", other);
        consumer.showCustomOption(QCodeStyleSettings.class, "EXPRESSION_SEMICOLON_TRIM_SPACES", "Trim spaces before semicolon", other);
        consumer.showCustomOption(QCodeStyleSettings.class, "EXPRESSION_SEMICOLON_REMOVE_LINES", "Remove bank lines before semicolon", other);
    }

    @Override
    public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings modelSettings) {
        return new CodeStyleAbstractConfigurable(settings, modelSettings, this.getConfigurableDisplayName()) {
            @Override
            protected CodeStyleAbstractPanel createPanel(CodeStyleSettings settings) {
                return new QCodeStylePanel(getCurrentSettings(), settings);
            }
        };
    }

    private String name(Enum<?> en) {
        return en.name();
    }

    private String[] names(Enum<?>... enums) {
        return Stream.of(enums).map(Enum::name).toArray(String[]::new);
    }
}