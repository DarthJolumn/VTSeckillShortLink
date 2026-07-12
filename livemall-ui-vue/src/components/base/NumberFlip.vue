<template>
  <span class="numflip" :class="{ 'is-urgent': urgent }">
    <span v-for="(d, i) in digits" :key="i" class="numflip__digit" :style="{ '--h': d.h }">
      <span class="numflip__reel">
        <span v-for="n in 10" :key="n">{{ (n - 1) }}</span>
      </span>
    </span>
  </span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  urgent: { type: Boolean, default: false },
})
const digits = ref([])

function build(v) {
  const str = String(v || 0)
  return str.split('').map((ch) => ({ h: Number(ch) * 10 })) // 每位高度偏移
}

digits.value = build(props.value)
watch(() => props.value, (v) => { digits.value = build(v) })
</script>

<style scoped>
.numflip {
  display: inline-flex;
  font-family: var(--font-num);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.numflip__digit {
  position: relative;
  width: 0.62em;
  height: 1em;
  line-height: 1;
  overflow: hidden;
  display: inline-block;
}
.numflip__reel {
  position: absolute;
  top: 0; left: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  transform: translateY(calc(var(--h, 0) * -0.1em));
  transition: transform 0.5s var(--ease-out-expo);
}
.numflip__reel > span {
  height: 1em;
  line-height: 1;
  text-align: center;
  display: block;
}
.is-urgent .numflip__reel { transition: transform 0.2s var(--ease-out-expo); }
</style>
