import { TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { formatNumber } from '@angular/common';
import { appConfig } from './app.config';

describe('appConfig locale', () => {
  it('provides pl-PL LOCALE_ID with registered Polish locale data', () => {
    TestBed.configureTestingModule({ providers: [...appConfig.providers] });

    const locale = TestBed.inject(LOCALE_ID);
    expect(locale).toBe('pl-PL');
    // pl locale groups thousands with a non-breaking space and uses a decimal comma
    expect(formatNumber(1234.5, locale, '1.2-2')).toBe('1 234,50');
  });
});
