/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.rule.ws;

import com.google.common.collect.Maps;
import org.sonar.api.resources.Language;
import org.sonar.api.resources.Languages;
import org.sonar.api.server.debt.DebtCharacteristic;
import org.sonar.api.server.debt.DebtModel;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.markdown.Markdown;
import org.sonar.server.rule.Rule;
import org.sonar.server.rule.RuleParam;
import org.sonar.server.rule.index.RuleDoc;
import org.sonar.server.rule.index.RuleNormalizer;
import org.sonar.server.search.ws.BaseMapping;
import org.sonar.server.search.ws.SearchOptions;
import org.sonar.server.text.MacroInterpreter;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Conversion of {@link org.sonar.server.rule.index.RuleDoc} to WS JSON document
 */
public class RuleMapping extends BaseMapping<RuleDoc, RuleMappingContext> {

  private final DebtModel debtModel;

  public RuleMapping(final Languages languages, final MacroInterpreter macroInterpreter, final DebtModel debtModel) {
    this.debtModel = debtModel;

    mapBasicFields(languages);
    mapDescriptionFields(macroInterpreter);
    mapDebtFields();
    mapParamFields();
  }

  private void mapBasicFields(final Languages languages) {
    map("repo", RuleNormalizer.RuleField.REPOSITORY.field());
    map("name", RuleNormalizer.RuleField.NAME.field());
    mapDateTime("createdAt", RuleNormalizer.RuleField.CREATED_AT.field());
    map("severity", RuleNormalizer.RuleField.SEVERITY.field());
    map("status", RuleNormalizer.RuleField.STATUS.field());
    map("internalKey", RuleNormalizer.RuleField.INTERNAL_KEY.field());
    mapBoolean("isTemplate", RuleNormalizer.RuleField.IS_TEMPLATE.field());
    map("templateKey", RuleNormalizer.RuleField.TEMPLATE_KEY.field());
    mapArray("tags", RuleNormalizer.RuleField.TAGS.field());
    mapArray("sysTags", RuleNormalizer.RuleField.SYSTEM_TAGS.field());
    map("lang", RuleNormalizer.RuleField.LANGUAGE.field());
    map("langName", new IndexMapper<RuleDoc, RuleMappingContext>(RuleNormalizer.RuleField.LANGUAGE.field()) {
      @Override
      public void write(JsonWriter json, RuleDoc rule, RuleMappingContext context) {
        Language lang = languages.get(rule.language());
        json.prop("langName", lang != null ? lang.getName() : null);
      }
    });
  }

  private void mapDescriptionFields(final MacroInterpreter macroInterpreter) {
    map("htmlDesc", new Mapper<RuleDoc, RuleMappingContext>() {
      @Override
      public void write(JsonWriter json, RuleDoc rule, RuleMappingContext context) {
        if (rule.markdownDescription() != null) {
          json.prop("htmlDesc", macroInterpreter.interpret(Markdown.convertToHtml(rule.markdownDescription())));
        } else {
          json.prop("htmlDesc", macroInterpreter.interpret(rule.htmlDescription()));
        }
      }
    });
    map("mdDesc", RuleNormalizer.RuleField.MARKDOWN_DESCRIPTION.field());
    map("noteLogin", RuleNormalizer.RuleField.NOTE_LOGIN.field());
    map("mdNote", RuleNormalizer.RuleField.NOTE.field());
    map("htmlNote", new IndexMapper<RuleDoc, RuleMappingContext>(RuleNormalizer.RuleField.NOTE.field()) {
      @Override
      public void write(JsonWriter json, RuleDoc rule, RuleMappingContext context) {
        String markdownNote = rule.markdownNote();
        if (markdownNote != null) {
          json.prop("htmlNote", macroInterpreter.interpret(Markdown.convertToHtml(markdownNote)));
        }
      }
    });
  }

  private void mapDebtFields() {
    map("defaultDebtChar", new IndexStringMapper("defaultDebtChar", RuleNormalizer.RuleField.DEFAULT_CHARACTERISTIC.field()));
    map("defaultDebtSubChar", new IndexStringMapper("defaultDebtSubChar", RuleNormalizer.RuleField.DEFAULT_SUB_CHARACTERISTIC.field()));
    map("debtChar", new IndexStringMapper("debtChar", RuleNormalizer.RuleField.CHARACTERISTIC.field(),
      RuleNormalizer.RuleField.DEFAULT_CHARACTERISTIC.field()));
    map("debtSubChar", new IndexStringMapper("debtSubChar", RuleNormalizer.RuleField.SUB_CHARACTERISTIC.field(),
      RuleNormalizer.RuleField.DEFAULT_SUB_CHARACTERISTIC.field()));
    map("debtCharName", new CharacteristicNameMapper());
    map("debtSubCharName", new SubCharacteristicNameMapper());
    map("debtRemFn", new IndexStringMapper("debtRemFnType", RuleNormalizer.RuleField.DEBT_FUNCTION_TYPE.field(),
      RuleNormalizer.RuleField.DEFAULT_DEBT_FUNCTION_TYPE.field()));
    map("debtRemFn", new IndexStringMapper("debtRemFnCoeff", RuleNormalizer.RuleField.DEBT_FUNCTION_COEFFICIENT.field(),
      RuleNormalizer.RuleField.DEFAULT_DEBT_FUNCTION_COEFFICIENT.field()));
    map("debtRemFn", new IndexStringMapper("debtRemFnOffset", RuleNormalizer.RuleField.DEBT_FUNCTION_OFFSET.field(),
      RuleNormalizer.RuleField.DEFAULT_DEBT_FUNCTION_OFFSET.field()));
    map("defaultDebtRemFn", new IndexStringMapper("defaultDebtRemFnType", RuleNormalizer.RuleField.DEFAULT_DEBT_FUNCTION_TYPE.field()));
    map("defaultDebtRemFn", new IndexStringMapper("defaultDebtRemFnCoeff", RuleNormalizer.RuleField.DEFAULT_DEBT_FUNCTION_COEFFICIENT.field()));
    map("defaultDebtRemFn", new IndexStringMapper("defaultDebtRemFnOffset", RuleNormalizer.RuleField.DEFAULT_DEBT_FUNCTION_OFFSET.field()));
    map("effortToFixDescription", RuleNormalizer.RuleField.FIX_DESCRIPTION.field());
    map("debtOverloaded", new OverriddenMapper());
  }

  private void mapParamFields() {
    map("params", new IndexMapper<RuleDoc, RuleMappingContext>(RuleNormalizer.RuleField.PARAMS.field()) {
      @Override
      public void write(JsonWriter json, RuleDoc rule, RuleMappingContext context) {
        json.name("params").beginArray();
        for (RuleParam param : rule.params()) {
          json
            .beginObject()
            .prop("key", param.key())
            .prop("desc", param.description())
            .prop("defaultValue", param.defaultValue())
            .endObject();
        }
        json.endArray();
      }
    });
  }

  public void write(Rule rule, JsonWriter json, @Nullable SearchOptions options) {
    RuleMappingContext context = new RuleMappingContext();
    if (needDebtCharacteristicNames(options) && rule.debtCharacteristicKey() != null) {
      // load debt characteristics if requested
      context.add(debtModel.characteristicByKey(rule.debtCharacteristicKey()));
    }
    if (needDebtSubCharacteristicNames(options) && rule.debtSubCharacteristicKey() != null) {
      context.add(debtModel.characteristicByKey(rule.debtSubCharacteristicKey()));
    }
    doWrite((RuleDoc) rule, context, json, options);
  }

  public void write(Collection<Rule> rules, JsonWriter json, @Nullable SearchOptions options) {
    if (!rules.isEmpty()) {
      RuleMappingContext context = new RuleMappingContext();
      if (needDebtCharacteristicNames(options) || needDebtSubCharacteristicNames(options)) {
        // load all debt characteristics
        context.addAll(debtModel.allCharacteristics());
      }
      for (Rule rule : rules) {
        doWrite((RuleDoc) rule, context, json, options);
      }
    }
  }

  private boolean needDebtCharacteristicNames(@Nullable SearchOptions options) {
    return options == null || options.hasField("debtCharName");
  }

  private boolean needDebtSubCharacteristicNames(@Nullable SearchOptions options) {
    return options == null || options.hasField("debtSubCharName");
  }

  private static class CharacteristicNameMapper extends IndexMapper<RuleDoc, RuleMappingContext> {
    private CharacteristicNameMapper() {
      super(RuleNormalizer.RuleField.CHARACTERISTIC.field(), RuleNormalizer.RuleField.DEFAULT_CHARACTERISTIC.field());
    }

    @Override
    public void write(JsonWriter json, RuleDoc rule, RuleMappingContext context) {
      json.prop("debtCharName", context.debtCharacteristicName(rule.debtCharacteristicKey()));
    }
  }

  private static class SubCharacteristicNameMapper extends IndexMapper<RuleDoc, RuleMappingContext> {
    private SubCharacteristicNameMapper() {
      super(RuleNormalizer.RuleField.SUB_CHARACTERISTIC.field(), RuleNormalizer.RuleField.DEFAULT_SUB_CHARACTERISTIC.field());
    }

    @Override
    public void write(JsonWriter json, RuleDoc rule, RuleMappingContext context) {
      json.prop("debtSubCharName", context.debtCharacteristicName(rule.debtSubCharacteristicKey()));
    }
  }

  private static class OverriddenMapper implements Mapper<RuleDoc, RuleMappingContext> {
    @Override
    public void write(JsonWriter json, RuleDoc rule, RuleMappingContext context) {
      json.prop("debtOverloaded", rule.debtOverloaded());
    }
  }
}

class RuleMappingContext {
  private final Map<String, String> debtCharacteristicNamesByKey = Maps.newHashMap();

  @CheckForNull
  public String debtCharacteristicName(String key) {
    return debtCharacteristicNamesByKey.get(key);
  }

  void add(@Nullable DebtCharacteristic c) {
    if (c != null) {
      debtCharacteristicNamesByKey.put(c.key(), c.name());
    }
  }

  void addAll(Collection<DebtCharacteristic> coll) {
    for (DebtCharacteristic c : coll) {
      add(c);
    }
  }
}
