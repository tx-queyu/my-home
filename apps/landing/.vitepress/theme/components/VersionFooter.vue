<script setup lang="ts">
import pkg from "../../../package.json";
const version = pkg.version;
</script>

<template>
  <div class="version-footer">v{{ version }}</div>
</template>

<style scoped>
.version-footer {
  text-align: center;
  font-size: 12px;
  color: var(--vp-c-text-3);
  padding: 24px 0 16px;
}
</style>
