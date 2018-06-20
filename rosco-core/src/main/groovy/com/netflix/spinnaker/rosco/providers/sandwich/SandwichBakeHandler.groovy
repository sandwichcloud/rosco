/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.providers.sandwich

import com.netflix.spinnaker.rosco.api.Bake
import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler
import com.netflix.spinnaker.rosco.providers.sandwich.config.RoscoSandwichConfiguration
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory
import com.netflix.spinnaker.rosco.providers.util.PackerArtifactService
import com.netflix.spinnaker.rosco.providers.util.PackerManifest
import com.netflix.spinnaker.rosco.providers.util.PackerManifestService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class SandwichBakeHandler extends CloudProviderBakeHandler {

  private static final String IMAGE_NAME_TOKEN = 'sandwich: An image was created:'

  ImageNameFactory imageNameFactory = new ImageNameFactory()

  PackerArtifactService packerArtifactService = new PackerArtifactService()

  PackerManifestService packerManifestService = new PackerManifestService()

  @Autowired
  RoscoSandwichConfiguration.SandwichBakeryDefaults sandwichBakeryDefaults

  @Override
  def getBakeryDefaults() {
    return sandwichBakeryDefaults
  }

  @Override
  BakeOptions getBakeOptions() {
    new BakeOptions(
      cloudProvider: BakeRequest.CloudProviderType.sandwich,
      baseImages: sandwichBakeryDefaults?.baseImages?.collect { it.baseImage }
    )
  }

  @Override
  String produceProviderSpecificBakeKeyComponent(String region, BakeRequest bakeRequest) {
    region
  }

  @Override
  def findVirtualizationSettings(String region, BakeRequest bakeRequest) {
    def sandwichOperatingSystemVirtualizationSettings = sandwichBakeryDefaults?.baseImages.find {
      it.baseImage.id == bakeRequest.base_os
    }

    if (!sandwichOperatingSystemVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for '$bakeRequest.base_os'.")
    }

    def sandwichVirtualizationSettings = sandwichOperatingSystemVirtualizationSettings?.virtualizationSettings.find {
      it.region == region
    }

    if (!sandwichVirtualizationSettings) {
      throw new IllegalArgumentException("No virtualization settings found for region '$region' and operating system '$bakeRequest.base_os'.")
    }

    if (bakeRequest.base_ami) {
      sandwichVirtualizationSettings = sandwichVirtualizationSettings.clone()
      sandwichVirtualizationSettings.sourceImage = bakeRequest.base_ami
    }

    return sandwichVirtualizationSettings
  }

  @Override
  Map buildParameterMap(String region,
                        def sandwichVirtualizationSettings, String imageName, BakeRequest bakeRequest, String appVersionStr) {
    def parameterMap = [
      sandwich_api_server: sandwichBakeryDefaults.apiServer,
      sandwich_token: sandwichBakeryDefaults.token,
      sandwich_region: region,
      sandwich_project: sandwichBakeryDefaults.project,
      sandwich_network: sandwichBakeryDefaults.network,
      sandwich_flavor: sandwichVirtualizationSettings.flavor,
      sandwich_source_image: sandwichVirtualizationSettings.sourceImage,
      sandwich_image_name: imageName
    ]

    parameterMap.manifestFile = packerManifestService.getManifestFileName(bakeRequest.request_id)

    return parameterMap
  }

  @Override
  void deleteArtifactFile(String bakeId) {
    packerArtifactService.deleteArtifactFile(bakeId)
  }

  @Override
  String getTemplateFileName(BakeOptions.BaseImage baseImage) {
    return baseImage.templateFile ?: sandwichBakeryDefaults.templateFile
  }

  @Override
  Bake scrapeCompletedBakeResults(String region, String bakeId, String logsContent) {
    String imageName

    if (packerManifestService.manifestExists(bakeId)) {
      log.info("Using manifest file to determine baked artifact for bake $bakeId")
      PackerManifest.PackerBuild packerBuild = packerManifestService.getBuild(bakeId)
      imageName = packerBuild.getArtifactId()
    } else {
      // TODO(duftler): Presently scraping the logs for the image name. Would be better to not be reliant on the log
      // format not changing. Resolve this by storing bake details in redis.
      log.info("Scraping logs to determine baked artifact for bake $bakeId")
      logsContent.eachLine { String line ->
        if (line =~ IMAGE_NAME_TOKEN) {
          imageName = line.split(" ").last()
        }
      }
    }

    return new Bake(id: bakeId, image_name: imageName)
  }

  @Override
  List<String> getMaskedPackerParameters() {
    ['sandwich_token']
  }
}
