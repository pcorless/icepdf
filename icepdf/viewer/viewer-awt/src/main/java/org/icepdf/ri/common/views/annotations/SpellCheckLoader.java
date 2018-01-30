/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.views.annotations;

import javax.swing.text.JTextComponent;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Utility class that uses reflection to load the http://jortho.sourceforge.net/ library.  The Jortho project
 * allows of the easy wrapping of JTextComponent with a spell check feature.  The library and dictionary are
 * a fairly large size so this class ues reflection to try and load the library when available on the class path.
 *
 * @since 6.3
 */
public class SpellCheckLoader {

    private static final Logger logger =
            Logger.getLogger(SpellCheckLoader.class.toString());

    protected static final String JORTHO_SPELL_CHECKER_CLASS = "com.inet.jortho.SpellChecker";
    protected static final String JORTHO_FILE_USER_DICTIONARY_CLASS = "com.inet.jortho.UserDictionaryProvider";
    protected static final String JORTHO_REGISTER_DICTIONARY_METHOD = "registerDictionaries";
    protected static final String JORTHO_ADD_SPELL_CHECKER_METHOD = "register";
    protected static final String JORTHO_SET_USER_DICTIONARY_PROVIDER_METHOD = "setUserDictionaryProvider";

    public static void addSpellChecker(JTextComponent textComponent) {
        try {
            Class<?> spellCheckerClass = Class.forName(JORTHO_SPELL_CHECKER_CLASS);
            Method addSpellCheckMethod = spellCheckerClass.getMethod(JORTHO_ADD_SPELL_CHECKER_METHOD, JTextComponent.class);
            addSpellCheckMethod.invoke(null, textComponent);
        } catch (Exception e) {
            logger.info(JORTHO_SPELL_CHECKER_CLASS + " could not be found on the class path");
        }
    }

    /**
     * Registers the available dictionaries. The dictionaries' URLs must have the form "dictionary_xx.xxxxx" and must
     * be relative to the baseURL. The available languages and extension of the dictionaries is load from a
     * configuration file. The configuration file must also relative to the baseURL and must be named
     * dictionaries.cnf, dictionaries.properties or dictionaries.txt. If the dictionary of the active Locale does
     * not exist, the first dictionary is loaded. There is only one dictionary loaded in memory at a given time.
     * You can download the dictionary files from http://sourceforge.net/projects/jortho/files/Dictionaries/ The
     * configuration file has a Java Properties format. Currently there are the follow options: languages, extension.
     *
     * @param baseURL      base url that contains at least one dictionary_xx.xxxxx file.
     * @param activeLocale the locale that should be loaded and made active. If null or empty then the default locale is used.
     */
    public static void registerDictionaries(URL baseURL,
                                            String activeLocale) {
        try {
            Class<?> spellCheckerClass = Class.forName(JORTHO_SPELL_CHECKER_CLASS);
            Method addSpellCheckMethod = spellCheckerClass.getMethod(JORTHO_REGISTER_DICTIONARY_METHOD, URL.class, String.class);
            addSpellCheckMethod.invoke(null, baseURL, activeLocale);
        } catch (Exception e) {
            logger.info(JORTHO_SPELL_CHECKER_CLASS + " could not be found on the class path");
        }
    }

    /**
     * Sets the UserDictionaryProvider. This is needed if the user should be able to add their own words.
     * This method must be called before registerDictionaries(URL, String, String).
     *
     * @param fileUserDictionary the new UserDictionaryProvider or null
     */
    public static void setUserDictionaryProvider(Object fileUserDictionary) {
        try {
            Class<?> spellCheckerClass = Class.forName(JORTHO_SPELL_CHECKER_CLASS);
            Class<?> fileUserDictionaryClass = Class.forName(JORTHO_FILE_USER_DICTIONARY_CLASS);
            Method addSpellCheckMethod = spellCheckerClass.getMethod(JORTHO_SET_USER_DICTIONARY_PROVIDER_METHOD, fileUserDictionaryClass);
            addSpellCheckMethod.invoke(null, fileUserDictionary);
        } catch (Exception e) {
            e.printStackTrace();
            logger.info(JORTHO_SET_USER_DICTIONARY_PROVIDER_METHOD + " could not be found on the class path");
        }
    }
}
