/*
 * CBOMkit
 * Copyright (C) 2026 PQCA
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ibm.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.pqca.errors.CBOMSerializationFailed;
import org.pqca.scanning.Language;
import org.pqca.sdk.CBOMGenerationException;
import org.pqca.sdk.CBOMGenerator;
import org.pqca.sdk.CBOMScanOptions;
import org.pqca.sdk.CBOMScanReport;

public final class CBOMKitCli {
    private static final String DEFAULT_THEIA_EXECUTABLE = "cbomkit-theia";

    private CBOMKitCli() {}

    public static void main(@Nonnull String[] args) {
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        System.exit(new CBOMKitCliRunner().run(List.of(args)));
    }

    private static final class CBOMKitCliRunner {

        int run(@Nonnull List<String> args) {
            if (args.isEmpty() || hasHelp(args)) {
                printUsage();
                return args.isEmpty() ? 1 : 0;
            }

            final String command = args.getFirst();
            final CliArguments cliArguments = new CliArguments(args.subList(1, args.size()));
            try {
                return switch (command) {
                    case "source" -> generateSourceCBOM(cliArguments);
                    case "dir-assets" -> generateTheiaCBOM("dir", cliArguments);
                    case "image" -> generateTheiaCBOM("image", cliArguments);
                    default -> {
                        System.err.println("Unknown command: " + command);
                        printUsage();
                        yield 1;
                    }
                };
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println("CBOM generation failed: " + e.getMessage());
                return 1;
            }
        }

        private static boolean hasHelp(@Nonnull List<String> args) {
            return args.contains("-h") || args.contains("--help");
        }

        private static int generateSourceCBOM(@Nonnull CliArguments args)
                throws CBOMGenerationException, IOException, CBOMSerializationFailed {
            final Path directory =
                    Path.of(args.firstValue().orElseThrow(() -> missing("directory")));
            final CBOMScanOptions.Builder options = CBOMScanOptions.builder();
            args.values("language").ifPresent(values -> options.languages(parseLanguages(values)));
            args.values("exclude").ifPresent(options::excludePatterns);
            args.values("java-jar").ifPresent(options::javaDependencyJars);
            args.values("java-class-dir").ifPresent(options::javaClassDirectories);
            args.values("cxx-define").ifPresent(options::cxxDefines);
            options.requireJavaBuild(args.hasFlag("require-java-build"));
            args.value("git-url").ifPresent(options::gitUrl);
            args.value("revision").ifPresent(options::revision);
            args.value("commit").ifPresent(options::commit);
            args.value("subfolder").ifPresent(options::subFolder);

            final CBOMScanReport report = new CBOMGenerator().generate(directory, options.build());
            writeOutput(args.output(), report.cbom().toJSON().toPrettyString());
            System.err.printf(
                    "Scanned %d files, %d lines in %d ms%n",
                    report.numberOfScannedFiles(),
                    report.numberOfScannedLines(),
                    report.durationMillis());
            return 0;
        }

        private static int generateTheiaCBOM(
                @Nonnull String theiaCommand, @Nonnull CliArguments args)
                throws IOException, InterruptedException {
            final String target = args.firstValue().orElseThrow(() -> missing("target"));
            final String theiaExecutable =
                    args.value("theia")
                            .or(() -> Optional.ofNullable(System.getenv("CBOMKIT_THEIA")))
                            .orElse(DEFAULT_THEIA_EXECUTABLE);

            final List<String> command = new ArrayList<>();
            command.add(theiaExecutable);
            command.add(theiaCommand);
            command.add(target);
            args.value("bom").ifPresent(value -> addOption(command, "--bom", value));
            args.value("docker-host")
                    .ifPresent(value -> addOption(command, "--docker_host", value));
            args.values("plugin")
                    .ifPresent(
                            values ->
                                    values.forEach(
                                            value -> addOption(command, "--plugins", value)));
            args.values("ignore")
                    .ifPresent(
                            values ->
                                    values.forEach(value -> addOption(command, "--ignore", value)));

            final Process process =
                    new ProcessBuilder(command)
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .start();
            final String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("cbomkit-theia exited with code " + exitCode);
            }
            writeOutput(args.output(), normalizeTheiaCBOM(output));
            return 0;
        }

        @Nonnull
        private static String normalizeTheiaCBOM(@Nonnull String output) throws IOException {
            final JsonNode cbom = new ObjectMapper().readTree(output);
            if (cbom instanceof ObjectNode objectNode) {
                objectNode.put("specVersion", "1.7");
                return objectNode.toPrettyString();
            }
            return output;
        }

        private static void addOption(
                @Nonnull List<String> command, @Nonnull String option, @Nonnull String value) {
            command.add(option);
            command.add(value);
        }

        @Nonnull
        private static EnumSet<Language> parseLanguages(@Nonnull List<String> values) {
            final EnumSet<Language> languages = EnumSet.noneOf(Language.class);
            for (String value : values) {
                for (String language : value.split(",")) {
                    languages.add(Language.valueOf(language.trim().toUpperCase(Locale.ROOT)));
                }
            }
            return languages;
        }

        private static void writeOutput(@Nonnull Optional<Path> output, @Nonnull String cbom)
                throws IOException {
            if (output.isPresent()) {
                Files.writeString(output.get(), cbom, StandardCharsets.UTF_8);
            } else {
                System.out.println(cbom);
            }
        }

        @Nonnull
        private static IllegalArgumentException missing(@Nonnull String argument) {
            return new IllegalArgumentException("Missing required " + argument);
        }

        private static void printUsage() {
            System.err.println(
                    """
                    Usage:
                      cbomkit source <directory> [--output bom.json] [--language java,python,go,cxx]
                          [--exclude <regex>] [--java-jar <path-or-glob>] [--java-class-dir <dir>]
                          [--cxx-define <NAME[=VALUE]>] [--require-java-build] [--subfolder <path>]

                      cbomkit dir-assets <directory> [--output bom.json] [--theia /path/cbomkit-theia]
                          [--bom source.cbom.json] [--plugin <name>] [--ignore <glob>]

                      cbomkit image <image-ref> [--output bom.json] [--theia /path/cbomkit-theia]
                          [--bom source.cbom.json] [--plugin <name>] [--ignore <glob>] [--docker-host <uri>]
                    """);
        }
    }

    private static final class CliArguments {
        @Nonnull private final List<String> positional = new ArrayList<>();
        @Nonnull private final List<Option> options = new ArrayList<>();

        CliArguments(@Nonnull List<String> args) {
            for (int i = 0; i < args.size(); i++) {
                final String arg = args.get(i);
                if (!arg.startsWith("--")) {
                    positional.add(arg);
                    continue;
                }

                final String name = arg.substring(2);
                if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
                    options.add(new Option(name, null));
                } else {
                    options.add(new Option(name, args.get(++i)));
                }
            }
        }

        @Nonnull
        Optional<String> firstValue() {
            return positional.stream().findFirst();
        }

        boolean hasFlag(@Nonnull String name) {
            return options.stream().anyMatch(option -> option.name().equals(name));
        }

        @Nonnull
        Optional<String> value(@Nonnull String name) {
            return values(name).flatMap(values -> values.stream().findFirst());
        }

        @Nonnull
        Optional<List<String>> values(@Nonnull String name) {
            final List<String> values =
                    options.stream()
                            .filter(option -> option.name().equals(name))
                            .map(Option::value)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList();
            return values.isEmpty() ? Optional.empty() : Optional.of(values);
        }

        @Nonnull
        Optional<Path> output() {
            return value("output").map(Path::of);
        }
    }

    private record Option(@Nonnull String name, @Nullable String rawValue) {
        @Nonnull
        Optional<String> value() {
            return Optional.ofNullable(rawValue);
        }
    }
}
