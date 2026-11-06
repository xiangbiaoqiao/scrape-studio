<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { api, ApiError } from '@/api/client';
import type { CreateProjectInput, ProjectDetail } from '@/api/types';

const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  created: [project: ProjectDetail];
}>();

const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive<CreateProjectInput>({
  name: '',
  url: '',
  description: '',
});

const rules: FormRules = {
  name: [{ required: true, message: 'Give the project a name', trigger: 'blur' }],
  url: [
    { required: true, message: 'Enter the page you want to scrape', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value) {
          callback();
          return;
        }
        const candidate = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(value)
          ? value
          : `https://${value}`;
        try {
          const parsed = new URL(candidate);
          if (!['http:', 'https:'].includes(parsed.protocol)) {
            throw new Error('protocol');
          }
          callback();
        } catch {
          callback(new Error('Use a valid http:// or https:// URL'));
        }
      },
      trigger: 'blur',
    },
  ],
};

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

function reset(): void {
  form.name = '';
  form.url = '';
  form.description = '';
  formRef.value?.clearValidate();
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }

  loading.value = true;
  try {
    const project = await api.createProject({
      name: form.name.trim(),
      url: form.url.trim(),
      description: form.description?.trim() || null,
    });
    ElMessage.success('Project created');
    emit('created', project);
    visible.value = false;
    reset();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'Could not create the project');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <el-dialog v-model="visible" width="560px" :close-on-click-modal="false" @closed="reset">
    <template #header>
      <div class="dialog-title">
        <h3>New scraping project</h3>
        <p>Point ScrapeStudio at a page, then pick fields visually.</p>
      </div>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="Project name" prop="name">
        <el-input v-model="form.name" placeholder="Books to Scrape" maxlength="120" />
      </el-form-item>
      <el-form-item label="Start URL" prop="url">
        <el-input v-model="form.url" placeholder="https://books.toscrape.com/" />
      </el-form-item>
      <el-form-item label="Description (optional)">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="1000"
          placeholder="What are you collecting, and why?"
        />
      </el-form-item>
    </el-form>

    <div class="hint">
      <span class="dot" />
      The rendering worker opens the page in Chromium, so JavaScript-heavy sites work too.
    </div>

    <template #footer>
      <el-button @click="visible = false">Cancel</el-button>
      <el-button type="primary" :loading="loading" @click="submit">Create project</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-title h3 {
  margin: 0;
  font-size: 18px;
  letter-spacing: -0.02em;
}

.dialog-title p {
  margin: 5px 0 0;
  color: var(--ss-muted);
  font-size: 13px;
}

.hint {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  margin-top: 4px;
  padding: 11px 13px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12.5px;
  line-height: 1.5;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6366f1;
  margin-top: 5px;
  flex-shrink: 0;
}
</style>
