// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const sonarjs = require('eslint-plugin-sonarjs');

/**
 * ESLint for the portal.
 *
 * `npm run lint` existed as a script but could not work: ESLint was not a dependency and there was no
 * config at all. This is the offline substitute for the SonarQube server the team does not run, which is
 * why `eslint-plugin-sonarjs` is included — it supplies the cognitive-complexity, duplicate-branch and
 * identical-function rules that would otherwise need a server.
 *
 * SEVERITY POLICY. Rules that catch BUGS are errors. Rules that express STYLE or that would need a wide
 * refactor to satisfy are warnings, so that `npm run lint` is usable from the first run rather than
 * drowning in 126 pre-existing findings. A lint command whose first run prints thousands of errors gets
 * ignored, and an ignored linter catches nothing. Tighten these to error as the debt is paid down.
 */
module.exports = tseslint.config(
  {
    // Build output, dependencies and generated reports are not ours to lint.
    ignores: [
      'dist/**',
      'node_modules/**',
      'playwright-report/**',
      'test-results/**',
      '.angular/**',
      'coverage/**',
    ],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...angular.configs.tsRecommended,
      sonarjs.configs.recommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      // ── Angular conventions ──────────────────────────────────────────────────────────────────
      '@angular-eslint/directive-selector': [
        'warn',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'warn',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],

      // ── Bug-class: these stay ERRORS ─────────────────────────────────────────────────────────
      // A caught error that is neither handled nor rethrown is how `report-builder` swallowed a 404
      // and let `canExport` fall through to `return true`, so "no rows" read as "everyone may export".
      // Treated as a potential silent-permission bug, not a style nit.
      'no-empty': ['error', { allowEmptyCatch: false }],
      'sonarjs/no-identical-conditions': 'error',
      'sonarjs/no-identical-expressions': 'error',
      'sonarjs/no-all-duplicated-branches': 'error',
      'sonarjs/no-element-overwrite': 'error',
      'sonarjs/no-ignored-return': 'error',
      'sonarjs/no-use-of-empty-return-value': 'error',
      'sonarjs/non-existent-operator': 'error',
      'sonarjs/no-invariant-returns': 'error',
      'sonarjs/in-operator-type-error': 'error',
      'sonarjs/operation-returning-nan': 'error',
      'sonarjs/misplaced-loop-counter': 'error',
      'sonarjs/for-loop-increment-sign': 'error',
      'sonarjs/new-operator-misuse': 'error',

      // ── Weak typing: WARN for now, 126 pre-existing occurrences ──────────────────────────────
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],

      // ── Smell-class: WARN, they are judgement calls a reviewer should make ───────────────────
      'sonarjs/cognitive-complexity': ['warn', 20],
      'sonarjs/no-duplicate-string': ['warn', { threshold: 5 }],
      'sonarjs/no-identical-functions': 'warn',
      'sonarjs/no-nested-template-literals': 'off',
      'sonarjs/todo-tag': 'off',

      // Angular templates legitimately reference members the compiler sees but ESLint does not.
      '@typescript-eslint/no-unused-expressions': 'off',
    },
  },
  {
    files: ['**/*.html'],
    extends: [
      ...angular.configs.templateRecommended,
      ...angular.configs.templateAccessibility,
    ],
    rules: {
      // Accessibility is a real obligation for a government service, but it is a pre-existing debt of
      // its own: surfaced as warnings so it is visible and countable without blocking the command.
      '@angular-eslint/template/click-events-have-key-events': 'warn',
      '@angular-eslint/template/interactive-supports-focus': 'warn',
      '@angular-eslint/template/label-has-associated-control': 'warn',
      '@angular-eslint/template/alt-text': 'error',
    },
  },
  {
    // E2E specs are Node-context Playwright tests, not browser app code.
    files: ['e2e/**/*.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      'sonarjs/no-duplicate-string': 'off',
      'sonarjs/cognitive-complexity': 'off',
    },
  },
);
