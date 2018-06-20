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

package com.netflix.spinnaker.rosco.providers.sandwich.config

import com.netflix.spinnaker.rosco.api.BakeOptions
import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry
import com.netflix.spinnaker.rosco.providers.sandwich.SandwichBakeHandler
import groovy.transform.AutoClone
import groovy.transform.AutoCloneStyle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

@Configuration
@ConditionalOnProperty('sandwich.enabled')
@ComponentScan('com.netflix.spinnaker.rosco.providers.sandwich')
class RoscoSandwichConfiguration {

  @Autowired
  CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry

  @Autowired
  SandwichBakeHandler sandwichBakeHandler

  @Bean
  @ConfigurationProperties('sandwich.bakeryDefaults')
  SandwichBakeryDefaults sandwichBakeryDefaults() {
    new SandwichBakeryDefaults()
  }

  static class SandwichBakeryDefaults {
    String apiServer
    String token
    String project
    String network
    String templateFile
    List<SandwichOperatingSystemVirtualizationSettings> baseImages = []
  }

  static class SandwichOperatingSystemVirtualizationSettings {
    BakeOptions.BaseImage baseImage
    List<SandwichVirtualizationSettings> virtualizationSettings = []
  }

  @AutoClone(style = AutoCloneStyle.SIMPLE)
  static class SandwichVirtualizationSettings {
    String region
    String flavor
    String sourceImage
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(BakeRequest.CloudProviderType.sandwich, sandwichBakeHandler)
  }

}
