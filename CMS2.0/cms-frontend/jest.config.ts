import type { Config } from 'jest';

const config: Config = {
  testEnvironment: 'jsdom',
  setupFiles: ['<rootDir>/src/test-mocks/setup.ts'],
  roots: ['<rootDir>/src'],
  testMatch: ['**/*.spec.ts'],
  moduleFileExtensions: ['ts', 'js', 'json'],
  transform: {
    '^.+\\.ts$': ['ts-jest', {
      tsconfig: 'tsconfig.spec.json',
      useESM: false,
    }],
  },
  transformIgnorePatterns: [
    'node_modules/(?!(@angular|rxjs)/)',
  ],
  moduleNameMapper: {
    '^@angular/core$': '<rootDir>/src/test-mocks/angular-core.mock.ts',
    '^@angular/common/http$': '<rootDir>/src/test-mocks/angular-http.mock.ts',
    '^@angular/forms$': '<rootDir>/src/test-mocks/angular-forms.mock.ts',
    '^rxjs$': '<rootDir>/src/test-mocks/rxjs.mock.ts',
    '^rxjs/operators$': '<rootDir>/src/test-mocks/rxjs-operators.mock.ts',
  },
};

export default config;
