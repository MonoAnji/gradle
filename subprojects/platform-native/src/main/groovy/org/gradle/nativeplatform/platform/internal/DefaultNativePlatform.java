/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.platform.internal;

import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.gradle.internal.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultNativePlatform implements NativePlatformInternal {
    private final String name;
    private ArchitectureInternal architecture;
    private OperatingSystemInternal operatingSystem;
    private static Set<DefaultNativePlatform> defaults = defaultPlatformDefinitions();
    private static DefaultNativePlatform defaultNativePlatform;

    public static Set<DefaultNativePlatform> defaultPlatformDefinitions() {
        //TODO freekh: move this code to somewhere else, or use configuration to load this data instead.
        //TODO freekh: add itanium? Who on earth uses it these days? It was discontinued in 2012 so...
        //TODO freekh: add more ppc? xbox/playstation is based on Power arch (ppc/cell) I think?
        Set<DefaultNativePlatform> platforms = new LinkedHashSet<DefaultNativePlatform>();

        OperatingSystemInternal windows = new DefaultOperatingSystem("windows");
        OperatingSystemInternal freebsd = new DefaultOperatingSystem("freebsd");
        OperatingSystemInternal linux = new DefaultOperatingSystem("linux");
        OperatingSystemInternal osx = new DefaultOperatingSystem("osx");
        OperatingSystemInternal unix = new DefaultOperatingSystem("unix");
        OperatingSystemInternal solaris = new DefaultOperatingSystem("solaris");

        ArchitectureInternal x86 = new DefaultArchitecture("x86");
        ArchitectureInternal x64 = new DefaultArchitecture("x86_64");
        ArchitectureInternal armv7 = new DefaultArchitecture("armv7");
        ArchitectureInternal armv8 = new DefaultArchitecture("armv8");
        ArchitectureInternal sparc = new DefaultArchitecture("sparc");
        ArchitectureInternal ultrasparc = new DefaultArchitecture("ultrasparc");
        ArchitectureInternal ppc = new DefaultArchitecture("ppc");
        ArchitectureInternal ppc64 = new DefaultArchitecture("ppc64");


        platforms.add(new DefaultNativePlatform("windows_x86", x86, windows));
        platforms.add(new DefaultNativePlatform("windows_x86_64", x64, windows));
        platforms.add(new DefaultNativePlatform("windows_rt_32", armv7, windows));

        platforms.add(new DefaultNativePlatform("freebsd_x86", x86, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_x86_64", x64, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_armv7", armv7, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_armv8", armv8, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_ppc", ppc, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_ppc64", ppc64, freebsd));

        platforms.add(new DefaultNativePlatform("unix_x86", x86, unix));
        platforms.add(new DefaultNativePlatform("unix_x86_64", x64, unix));
        platforms.add(new DefaultNativePlatform("unix_armv7", armv7, unix));
        platforms.add(new DefaultNativePlatform("unix_armv8", armv8, unix));
        platforms.add(new DefaultNativePlatform("unix_ppc", ppc, unix));
        platforms.add(new DefaultNativePlatform("unix_ppc64", ppc64, unix));

        platforms.add(new DefaultNativePlatform("linux_x64", x64, linux));
        platforms.add(new DefaultNativePlatform("linux_x86", x86, linux));
        platforms.add(new DefaultNativePlatform("linux_armv7", armv7, linux));
        platforms.add(new DefaultNativePlatform("linux_armv8", armv8, linux));

        platforms.add(new DefaultNativePlatform("osx_x86", x86, osx));
        platforms.add(new DefaultNativePlatform("osx_x64", x64, osx));

        platforms.add(new DefaultNativePlatform("solaris_x64", x64, solaris));
        platforms.add(new DefaultNativePlatform("solaris_x86", x86, solaris));
        platforms.add(new DefaultNativePlatform("solaris_sparc", sparc, solaris));
        platforms.add(new DefaultNativePlatform("solaris_ultrasparc", ultrasparc, solaris));

        return platforms;
    }

    public DefaultNativePlatform(String name, ArchitectureInternal architecture, OperatingSystemInternal operatingSystem) {
        this.name = name;
        this.architecture = architecture;
        this.operatingSystem = operatingSystem;
    }

    public DefaultNativePlatform(String name) {
        this(name, getDefault().getArchitecture(), getDefault().getOperatingSystem());
    }

    //TODO freekh: Move this logic back into grapefruit?
    private static OperatingSystem getPropertyBasedOperatingSystem() {
        String osName = System.getProperty("os.name").toLowerCase();
        OperatingSystem os = null;
        if (osName.contains("windows")) {
            os = OperatingSystem.WINDOWS;
        } else if (osName.contains("linux")) {
            os = OperatingSystem.LINUX;
        } else if (osName.contains("os x") || osName.contains("darwin")) {
            os = OperatingSystem.MAC_OS;
        } else if (osName.contains("freebsd")) {
            os = OperatingSystem.FREE_BSD;
        }
        //TODO: solaris seems to be missing (this code was inspired by grapefruit)?
        return os;
    }

    //TODO freekh: Move this logic back into grapefruit?
    private static ArchitectureInternal getPropertyBasedArchitecture() {
        ArchitectureInternal arch = null;
        String archName = System.getProperty("os.arch").toLowerCase();
        if (archName.equals("i386") || archName.equals("x86")) {
            arch = new DefaultArchitecture(archName);
        } else if (archName.equals("x86_64") || archName.equals("amd64") || archName.equals("universal")) {
            arch = new DefaultArchitecture(archName);
        }
        return arch;
    }

    private static DefaultNativePlatform findDefaultPlatform(final OperatingSystem os, final ArchitectureInternal architecture) {
        if (os != null) {
            DefaultNativePlatform matchingPlatform = (DefaultNativePlatform) CollectionUtils.find(defaults, new Predicate() {
                public boolean evaluate(Object object) {
                    DefaultNativePlatform platform = (DefaultNativePlatform) object;
                    return platform.architecture.equals(architecture)
                            && platform.operatingSystem.getInternalOs().equals(os);
                }
            });
            return matchingPlatform;
        } else {
            return null;
        }
    }

    private static DefaultNativePlatform assertNonNullPlatform(DefaultNativePlatform nativePlatform, String errorMsg) {
        if (nativePlatform == null) {
            throw new NativeIntegrationUnavailableException(errorMsg);
        } else {
            return nativePlatform;
        }
    }

    private final static String UNKNOWN_DEFAULT_PLATFORM_MSG = "Please specify a target platform.";

    //TODO freekh: Move this logic back into grapefruit?
    public static DefaultNativePlatform getDefault() {
        //TODO freekh: no need to synchronize,  because we can consider this to be idempotent
        if (defaultNativePlatform == null) {
            OperatingSystem os = getPropertyBasedOperatingSystem();
            ArchitectureInternal architectureInternal = getPropertyBasedArchitecture();
            DefaultNativePlatform propertyBasedDefault = null;
            if (architectureInternal != null) {
                propertyBasedDefault = findDefaultPlatform(os, architectureInternal);
            }
            if (propertyBasedDefault != null) {
                defaultNativePlatform = propertyBasedDefault;
            } else { //could not detect platform based on properties
                try {
                    //TODO freekh: Close streams!
                    //TODO freekh: Test for wmic/uname on Path?
                    if ((os != null && os.isWindows()) || File.separatorChar == '\\') { //guess Windows
                        Process archProcess  = Runtime.getRuntime().exec(new String[]{"wmic", "computersystem", "get", "systemtype"});
                        BufferedReader archReader = new BufferedReader(new InputStreamReader(archProcess.getInputStream()));
                        archReader.readLine();
                        archReader.readLine();
                        String archLine = archReader.readLine().toLowerCase();
                        if (archLine.contains("x64")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.WINDOWS, new DefaultArchitecture("x86_64")),
                                    "Could not find a default platform for what is believed to be 64-bit Windows on x86. " + UNKNOWN_DEFAULT_PLATFORM_MSG);
                        } else if (archLine.contains("x86")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.WINDOWS, new DefaultArchitecture("x86")),
                                    "Could not find a default platform for what is believed to be 32-bit Windows on x86. " + UNKNOWN_DEFAULT_PLATFORM_MSG);
                        } else if (archLine.contains("strongarm")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.WINDOWS, new DefaultArchitecture("armv7")),
                                    "Could not find a default platform for what is believed to be Windows on ARM. " + UNKNOWN_DEFAULT_PLATFORM_MSG);
                        }

                    } else { //guess Nix
                        Process systemProcess = Runtime.getRuntime().exec(new String[]{"uname", "-s"});
                        BufferedReader systemReader = new BufferedReader(new InputStreamReader(systemProcess.getInputStream()));
                        String systemLine = systemReader.readLine().toLowerCase();

                        Process machineProcess = Runtime.getRuntime().exec(new String[]{"uname", "-m"});
                        BufferedReader matchineReader = new BufferedReader(new InputStreamReader(machineProcess.getInputStream()));
                        String machineLine = matchineReader.readLine();
                        ArchitectureInternal arch = new DefaultArchitecture(machineLine);

                        String errorMsg = String.format("Could not find a default platform for %s architecture: %s. %s", systemLine, arch.getName(), UNKNOWN_DEFAULT_PLATFORM_MSG);
                        if (systemLine.contains("linux")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.LINUX, arch),
                                    errorMsg);
                        } else if (systemLine.contains("cygwin")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.WINDOWS, arch),
                                    errorMsg);
                        } else if (systemLine.contains("freebsd")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.FREE_BSD, arch),
                                    errorMsg);
                        } else if (systemLine.contains("sunos")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.SOLARIS, arch),
                                    errorMsg);
                        } else if (systemLine.contains("darwin")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.MAC_OS, arch),
                                    errorMsg);
                        } else if (!systemLine.isEmpty()) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.UNIX, arch),
                                    errorMsg);
                        }
                    }
                } catch (IOException e) {
                    throw new NativeIntegrationUnavailableException("Could not guess a default native platform. " + UNKNOWN_DEFAULT_PLATFORM_MSG);
                }
            }
        }
        return defaultNativePlatform;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getDisplayName() {
        return String.format("platform '%s'", name);
    }

    public ArchitectureInternal getArchitecture() {
        return architecture;
    }

    public void architecture(String name) {
        architecture = new DefaultArchitecture(name);
    }

    public OperatingSystemInternal getOperatingSystem() {
        return operatingSystem;
    }

    public void operatingSystem(String name) {
        operatingSystem = new DefaultOperatingSystem(name);
    }

    public String getCompatibilityString() {
        return String.format("%s:%s", getArchitecture().getName(), getOperatingSystem().getName());
    }
}
