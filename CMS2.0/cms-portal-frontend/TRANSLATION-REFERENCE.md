# Translation System Reference

## Architecture

Custom database-backed i18n system (NOT `@ngx-translate`). Translations stored in DB, served via REST API, consumed by Angular through a custom `TranslationService` and `TranslatePipe`.

## Flow

1. App startup: `TranslationService` reads locale from `localStorage` (key: `cms_locale`, default: `en`)
2. Fetches locales from `GET /api/v1/i18n/locales`
3. Fetches translations from `GET /api/v1/i18n/translations/{locale}`
4. Pipe `{{ 'key.name' | translate }}` renders translated text
5. Language switch: user selects locale -> saves to localStorage -> reloads translations -> sets RTL if needed

## Supported Locales (10)

| Code | Language | RTL |
|------|----------|-----|
| en | English | No |
| hi | Hindi | No |
| bn | Bengali | No |
| mr | Marathi | No |
| te | Telugu | No |
| ta | Tamil | No |
| gu | Gujarati | No |
| ur | Urdu | Yes |
| kn | Kannada | No |
| ml | Malayalam | No |

## API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/i18n/locales` | List supported locales |
| GET | `/api/v1/i18n/translations/{locale}` | All translations for a locale (flat key-value map) |
| GET | `/api/v1/i18n/translations/{locale}/{module}` | Translations filtered by module |
| POST | `/api/v1/i18n/translations` | Upsert single translation `{code, locale, value}` |
| POST | `/api/v1/i18n/translations/bulk` | Bulk upsert (max 1000) |
| POST | `/api/v1/i18n/keys` | Create new translation key |

## Frontend Files

| File | Purpose |
|------|---------|
| `src/app/services/translation.service.ts` | Core service (Angular signals-based, localStorage persistence) |
| `src/app/pipes/translate.pipe.ts` | Impure pipe for template bindings |
| `src/app/components/shared/language-switcher/language-switcher.component.ts` | Standalone dropdown component |
| `src/app/components/public/public-layout/public-layout.component.ts` | Layout with native `<select>` language selector |

## Backend Files

| File | Purpose |
|------|---------|
| `cms-backend/src/main/java/com/hrms/cms/controller/TranslationController.java` | REST controller |
| `cms-backend/src/main/java/com/hrms/cms/service/TranslationService.java` | Service with `@Cacheable` |
| `cms-backend/src/main/java/com/hrms/cms/entity/Translation.java` | JPA entity (table: `translations`) |
| `cms-backend/src/main/java/com/hrms/cms/entity/TranslationKey.java` | JPA entity (table: `translation_keys`) |
| `cms-backend/src/main/java/com/hrms/cms/entity/SupportedLocale.java` | Enum with 10 locales |
| `cms-backend/src/main/java/com/hrms/cms/repository/TranslationRepository.java` | JPQL queries |
| `cms-backend/src/main/java/com/hrms/cms/repository/TranslationKeyRepository.java` | Key CRUD |
| `cms-backend/src/main/java/com/hrms/cms/config/TranslationDataInitializer.java` | Order(2) - CSV seeder |
| `cms-backend/src/main/java/com/hrms/cms/config/Phase1TranslationSeeder.java` | Order(3) - complaint keys |
| `cms-backend/src/main/java/com/hrms/cms/config/Phase2WizardTranslationSeeder.java` | Order(4) - wizard keys |
| `cms-backend/src/main/java/com/hrms/cms/config/EligibilityTranslationSeeder.java` | Order(5) - eligibility + multi-locale |
| `cms-backend/src/main/java/com/hrms/cms/config/PortalFullTranslationSeeder.java` | Order(6) - full portal keys |
| `cms-backend/src/main/java/com/hrms/cms/config/TranslationCacheWarmup.java` | Order(7) - evicts cache after seeding |
| `cms-backend/src/main/resources/data/translations.csv` | 434 rows seed data (10 locales) |

## Data Seeding (dev-local)

Translations are seeded automatically via `CommandLineRunner` beans (Orders 2-6):
1. `TranslationDataInitializer` loads from `data/translations.csv` (118 keys)
2. Phase1/Phase2/Eligibility/PortalFull seeders add additional keys
3. `TranslationCacheWarmup` (Order 7) clears Spring cache after all seeding completes

No external DB or manual SQL scripts needed for dev-local (H2 in-memory).

## Known Issue & Fix (dev-local)

**Problem:** `@Cacheable` on `getTranslationsForLocale()` could cache an empty result if called before seeders finish.

**Fix:** `TranslationCacheWarmup` (Order 7) evicts the `translations` and `translations-module` caches after all seeders complete.

## Components Using the Translate Pipe

- public-layout, public-home, eligibility-wizard, file-complaint
- file-appeal, complaint-history, public-login, submit-feedback
- withdraw-complaint, staff-dashboard, draft-assessment (CRPC)

## Interpolation

The pipe supports `{{paramName}}` interpolation:
```html
{{ 'greeting.hello' | translate:{ name: userName } }}
```

## RTL Support

Urdu (`ur`) has `rtl: true`. The layout binds `[attr.dir]="translationService.isRtl() ? 'rtl' : null"` and sets `lang` attribute on `<html>`.
