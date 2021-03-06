/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tools.build.bundletool.validation;

import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestFusingException.BaseModuleExcludedFromFusingException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestFusingException.ModuleFusingConfigurationMissingException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MaxSdkInvalidException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MinSdkGreaterThanMaxSdkException;
import com.android.tools.build.bundletool.exceptions.manifest.ManifestSdkTargetingException.MinSdkInvalidException;
import com.android.tools.build.bundletool.manifest.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import java.util.Optional;

/** Validates {@code AndroidManifest.xml} file of each module. */
public class AndroidManifestValidator extends SubValidator {

  @Override
  public void validateModule(BundleModule module) {
    validateOnDemand(module);
    validateFusingConfig(module);
    validateMinMaxSdk(module);
  }

  private void validateOnDemand(BundleModule module) {
    Optional<Boolean> isDynamicModule =
        module
            .getAndroidManifest()
            .isOnDemandModule(
                BundleToolVersion.getVersionFromBundleConfig(module.getBundleConfig()));

    if (module.isBaseModule()) {
      // In the base module, onDemand must be either not set or false
      if (isDynamicModule.isPresent() && isDynamicModule.get()) {
        throw new ValidationException(
            "The base module cannot be marked as onDemand='true' since it will always be served.");
      }
    } else {
      // In feature modules, onDemand must be explicitly set to some value.
      if (!isDynamicModule.isPresent()) {
        throw ValidationException.builder()
            .withMessage(
                "The element <dist:module> in the AndroidManifest.xml must have the attribute "
                    + "'onDemand' explicitly set (module: '%s').",
                module.getName())
            .build();
      }
    }
  }

  private void validateFusingConfig(BundleModule module) {
    Optional<Boolean> includedInFusingByManifest =
        module
            .getAndroidManifest()
            .getIsModuleIncludedInFusing(
                BundleToolVersion.getVersionFromBundleConfig(module.getBundleConfig()));

    if (module.isBaseModule()) {
      if (includedInFusingByManifest.isPresent() && !includedInFusingByManifest.get()) {
        throw new BaseModuleExcludedFromFusingException();
      }
    } else {
      if (!includedInFusingByManifest.isPresent()) {
        throw new ModuleFusingConfigurationMissingException(module.getName().getName());
      }
    }
  }

  private void validateMinMaxSdk(BundleModule module) {
    AndroidManifest manifest = module.getAndroidManifest();
    Optional<Integer> maxSdk = manifest.getMaxSdkVersion();
    Optional<Integer> minSdk = manifest.getMinSdkVersion();

    maxSdk
        .filter(sdk -> sdk < 0)
        .ifPresent(
            sdk -> {
              throw new MaxSdkInvalidException(sdk);
            });

    minSdk
        .filter(sdk -> sdk < 0)
        .ifPresent(
            sdk -> {
              throw new MinSdkInvalidException(sdk);
            });

    if (maxSdk.isPresent() && minSdk.isPresent() && maxSdk.get() < minSdk.get()) {
      throw new MinSdkGreaterThanMaxSdkException(minSdk.get(), maxSdk.get());
    }
  }
}
