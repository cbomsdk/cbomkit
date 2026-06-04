<template>
  <div style="padding: 20px 10px">
    <!-- CODE -->
    <div>
      <div
        style="
          display: flex;
          align-items: center;
          margin-bottom: -6px;
        "
      >
        <h4 style="font-weight: 500">Code</h4>
        <cv-button
          v-if="hasCodeLocation()"
          class="code-button"
          kind="ghost"
          v-on:click="$emit('open-code', true)"
          style="margin-left: auto"
        >
          View code <Launch16 class="bx--btn__icon"
        /></cv-button>
      </div>
      <GithubEmbed v-if="hasCodeLocation()" :asset="asset" @open-code="$emit('open-code', false)" />
      <!-- Text when no code location was specified -->
      <div v-else style="margin-top: 20px; border-radius: 15px; background-color: rgba(0, 0, 0, 0.15); padding: 15px;">
        No code location has been specified in the CBOM for this cryptographic asset. If you include code location information in a CBOM from a public online repository, you will be able to preview the code here and open it directly on a service like GitHub.
      </div>
    </div>

    <!-- POLICY FINDINGS -->
    <div v-if="hasValidComplianceResults">
      <div
        style="
          display: flex;
          align-items: center;
          padding-top: 16px;
          padding-bottom: 6px;
        "
      >
        <h4 style="font-weight: 500">Compliance</h4>
      </div>

      <div style="display: flex; align-items: center; padding: 4px 10px 12px;">
        <ComplianceIcon
          :asset="asset"
          v-if="hasValidComplianceResults"
          style="margin-right:12px; scale: 1.2;"
        />
        <WatsonHealthImageAvailabilityUnavailable24 v-else/>
        <div>
          <div style="font-size: large">
            {{ getComplianceDescription(asset) }}
          </div>
          <div style="font-size: small">
            Policy: {{ getCompliancePolicyName }}
          </div>
        </div>
      </div>
    
      <div class="list" v-if="getComplianceFindingsWithMessage(asset).length>0" style="margin-bottom: -60px">
        <cv-structured-list condensed="true">
          <template slot="headings">
            <cv-structured-list-heading>Compliance Information</cv-structured-list-heading>
            <cv-structured-list-heading style="width: 25%">
              Category
            </cv-structured-list-heading>
          </template>
          <template slot="items">
            <cv-structured-list-item v-for="(finding, index) in getComplianceFindingsWithMessage(asset)" :key="index">
              <cv-structured-list-data>
                {{ finding.message }}
              </cv-structured-list-data>
              <cv-structured-list-data style="display: flex; align-items: center;">
                {{ getComplianceObjectFromId(finding.levelId).label }}
              </cv-structured-list-data>
            </cv-structured-list-item>
          </template>
        </cv-structured-list>
      </div>
      
    </div>

    <!-- DEPENDENCIES -->
     <div v-if="getBomRef">
      <DependenciesView :bomRef="getBomRef" @open-asset="openAsset"/>
     </div>

    <!-- SPECIFICATION -->
    <h4 style="font-weight: 500; padding-top: 16px; padding-bottom: 4px">
      Specification
    </h4>
    <div class="list">
      <cv-structured-list condensed="true">
        <template slot="headings">
          <cv-structured-list-heading style="width: 30%"
            >Type</cv-structured-list-heading
          >
          <cv-structured-list-heading>Value</cv-structured-list-heading>
        </template>
        <template slot="items">
          <cv-structured-list-item
            v-for="property in filteredProperties"
            :key="property.name"
          >
            <cv-structured-list-data>{{ property.name }}</cv-structured-list-data>
            <cv-structured-list-data>
              <div
                v-for="(value, index) in getPropertyValues(property.path)"
                :key="index"
                class="property-value"
              >
                <template v-if="isPlainObject(value)">
                  <div class="object-summary">{{ getObjectSummary(value) }}</div>
                  <div
                    v-for="(detail, detailIndex) in getObjectDetails(value)"
                    :key="detailIndex"
                    class="object-detail"
                  >
                    {{ detail }}
                  </div>
                </template>
                <template v-else>
                  {{ getFormattedValue(value) }}
                  <cv-tooltip
                    v-if="getTermDescription(value)"
                    :tip="getTermDescription(value)"
                    alignment="end"
                    class="tooltip"
                  >
                  </cv-tooltip>
                </template>
              </div>
            </cv-structured-list-data>
          </cv-structured-list-item>
        </template>
      </cv-structured-list>
    </div>
  </div>
</template>

<script>
import DependenciesView from "@/components/results/modal/DependenciesView.vue";
import {
  getTermFullName,
  getTermDescription,
  capitalizeFirstLetter,
  getPolicyResultsByAsset,
  hasValidComplianceResults,
  getComplianceDescription,
  getComplianceFindingsWithMessage,
  getCompliancePolicyName,
  getComplianceLabel,
  getComplianceObjectFromId,
  resolvePath
} from "@/helpers";
import {
  Launch16,
  WatsonHealthImageAvailabilityUnavailable24,
} from "@carbon/icons-vue";
import GithubEmbed from "@/components/results/modal/GithubEmbed.vue";
import ComplianceIcon from "@/components/results/ComplianceIcon.vue"

export default {
  name: "CryptoAssetDetails",
  data: function () {
    return {
      propertyPaths: /* ordered */ [
        { name: "Asset Type", path: "cryptoProperties.assetType" },
        /* algorithmProperties */
        { name: "Primitive", path: "cryptoProperties.algorithmProperties.primitive" },
        { name: "Parameter Set Identifier", path: "cryptoProperties.algorithmProperties.parameterSetIdentifier" },
        { name: "Curve", path: "cryptoProperties.algorithmProperties.curve" },
        { name: "Execution Environment", path: "cryptoProperties.algorithmProperties.executionEnvironment" },
        { name: "Implementation Platform", path: "cryptoProperties.algorithmProperties.implementationPlatform" },
        { name: "Certification Level", path: "cryptoProperties.algorithmProperties.certificationLevel" },
        { name: "Mode", path: "cryptoProperties.algorithmProperties.mode" },
        { name: "Padding", path: "cryptoProperties.algorithmProperties.padding" },
        { name: "Crypto Functions", path: "cryptoProperties.algorithmProperties.cryptoFunctions" },
        { name: "Classical Security Level", path: "cryptoProperties.algorithmProperties.classicalSecurityLevel" },
        { name: "NIST Quantum Security Level", path: "cryptoProperties.algorithmProperties.nistQuantumSecurityLevel" },
        /* certificateProperties */
        { name: "Subject Name", path: "cryptoProperties.certificateProperties.subjectName" },
        { name: "Issuer Name", path: "cryptoProperties.certificateProperties.issuerName" },
        { name: "Not Valid Before", path: "cryptoProperties.certificateProperties.notValidBefore" },
        { name: "Not Valid After", path: "cryptoProperties.certificateProperties.notValidAfter" },
        { name: "Signature Algorithm Reference", path: "cryptoProperties.certificateProperties.signatureAlgorithmRef" },
        { name: "Subject Public Key Reference", path: "cryptoProperties.certificateProperties.subjectPublicKeyRef" },
        { name: "Certificate Format", path: "cryptoProperties.certificateProperties.certificateFormat" },
        { name: "Certificate Extension", path: "cryptoProperties.certificateProperties.certificateExtension" },
        /* relatedCryptoMaterialProperties */
        { name: "Type", path: "cryptoProperties.relatedCryptoMaterialProperties.type" },
        { name: "ID", path: "cryptoProperties.relatedCryptoMaterialProperties.id" },
        { name: "State", path: "cryptoProperties.relatedCryptoMaterialProperties.state" },
        { name: "Algorithm Reference", path: "cryptoProperties.relatedCryptoMaterialProperties.algorithmRef" },
        { name: "Creation Date", path: "cryptoProperties.relatedCryptoMaterialProperties.creationDate" },
        { name: "Activation Date", path: "cryptoProperties.relatedCryptoMaterialProperties.activationDate" },
        { name: "Update Date", path: "cryptoProperties.relatedCryptoMaterialProperties.updateDate" },
        { name: "Expiration Date", path: "cryptoProperties.relatedCryptoMaterialProperties.expirationDate" },
        { name: "Value", path: "cryptoProperties.relatedCryptoMaterialProperties.value" },
        { name: "Size", path: "cryptoProperties.relatedCryptoMaterialProperties.size" },
        { name: "Format", path: "cryptoProperties.relatedCryptoMaterialProperties.format" },
        { name: "Secured By", path: "cryptoProperties.relatedCryptoMaterialProperties.securedBy" },
        /* protocolProperties */
        { name: "Type", path: "cryptoProperties.protocolProperties.type" },
        { name: "Version", path: "cryptoProperties.protocolProperties.version" },
        { name: "Cipher Suites", path: "cryptoProperties.protocolProperties.cipherSuites" },
        { name: "IKEv2 Transform Types", path: "cryptoProperties.protocolProperties.ikev2TransformTypes" },
        { name: "Cryptographic References", path: "cryptoProperties.protocolProperties.cryptoRefArray" },
        /* Other */
        { name: "OID", path: "cryptoProperties.oid" },
        { name: "BOM Reference", path: "bom-ref" },
      ]
    };
  },
  components: {
    DependenciesView,
    GithubEmbed,
    Launch16,
    WatsonHealthImageAvailabilityUnavailable24,
    ComplianceIcon,
  },
  props: {
    asset: null,
  },
  computed: {
    hasValidComplianceResults,
    getCompliancePolicyName,
    filteredProperties() {
      // Filter properties where the value exists
      return this.propertyPaths.filter(property => this.hasPropertyValues(property.path));
    },
    getBomRef() {
      if (this.asset === undefined || this.asset === null) {
        return
      }
      let values = this.getPropertyValues("bom-ref");
      if (values.length === 1) {
        return values[0];
      }
      return null;
    }
  },
  methods: {
    getTermFullName,
    getTermDescription,
    capitalizeFirstLetter,
    getPolicyResultsByAsset,
    getComplianceDescription,
    getComplianceFindingsWithMessage,
    getComplianceLabel,
    getComplianceObjectFromId,
    resolvePath,
    hasCodeLocation() {
      let occurences = this.getPropertyValues("evidence.occurrences")
      return occurences !== null && occurences !== undefined
    },
    // Utility method to safely access nested properties, and return an array of values
    getPropertyValues(path) {
      return resolvePath(this.asset, path);
    },
    hasPropertyValues(path) {
      const values = this.getPropertyValues(path);
      return values !== undefined && values !== null && (!Array.isArray(values) || values.length > 0);
    },
    isPlainObject(value) {
      return value !== null && typeof value === "object" && !Array.isArray(value);
    },
    getFormattedValue(value) {
      if (Array.isArray(value)) {
        return value.map(item => this.getFormattedValue(item)).join(", ");
      }
      return getTermFullName(value) ? getTermFullName(value) : value;
    },
    getObjectSummary(value) {
      if (Object.hasOwn(value, "name")) {
        return this.getFormattedValue(value.name);
      }
      return JSON.stringify(value);
    },
    getObjectDetails(value) {
      const detailFields = [
        { name: "Identifiers", key: "identifiers" },
        { name: "TLS Groups", key: "tlsGroups" },
        { name: "TLS Signature Schemes", key: "tlsSignatureSchemes" },
      ];
      const details = detailFields
        .filter(field => Array.isArray(value[field.key]) && value[field.key].length > 0)
        .map(field => `${field.name}: ${value[field.key].join(", ")}`);

      if (Array.isArray(value.algorithms) && value.algorithms.length > 0) {
        details.push(`Algorithms: ${value.algorithms.length} refs`);
      }
      return details;
    },
    openAsset(asset) {
      this.$emit('open-asset', asset);
    },
  },
};
</script>

<style scoped>
.tooltip {
  margin-left: 10px;
}

.property-value {
  padding-bottom: 8px;
}

.object-summary {
  font-weight: 500;
  overflow-wrap: anywhere;
}

.object-detail {
  color: #525252;
  font-size: 0.875rem;
  line-height: 1.35;
  overflow-wrap: anywhere;
}
</style>
