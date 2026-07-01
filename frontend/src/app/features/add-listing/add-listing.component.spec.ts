import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AddListingComponent } from './add-listing.component';
import { ListingService, Listing } from '../../services/listing.service';
import { PriceEstimateService, PriceEstimateResponse } from '../../services/price-estimate.service';

describe('AddListingComponent', () => {
  let listingStub: {
    createListing: ReturnType<typeof vi.fn>;
    uploadStlFiles: ReturnType<typeof vi.fn>;
  };
  let priceEstimateStub: { getEstimate: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  function createComponent(): AddListingComponent {
    return TestBed.runInInjectionContext(() => new AddListingComponent());
  }

  beforeEach(() => {
    vi.useFakeTimers();
    listingStub = { createListing: vi.fn(), uploadStlFiles: vi.fn() };
    priceEstimateStub = { getEstimate: vi.fn() };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: ListingService, useValue: listingStub },
        { provide: PriceEstimateService, useValue: priceEstimateStub },
        { provide: Router, useValue: router }
      ]
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function fileOf(name: string): File {
    return new File(['data'], name);
  }

  describe('form validation', () => {
    it('is invalid when empty', () => {
      const component = createComponent();
      expect(component.form.invalid).toBe(true);
    });

    it('enforces title min/max length', () => {
      const component = createComponent();
      component.titleCtrl.setValue('ab');
      expect(component.titleCtrl.hasError('minlength')).toBe(true);
      component.titleCtrl.setValue('a'.repeat(101));
      expect(component.titleCtrl.hasError('maxlength')).toBe(true);
      component.titleCtrl.setValue('Valid title');
      expect(component.titleCtrl.valid).toBe(true);
    });

    it('enforces description required/maxLength', () => {
      const component = createComponent();
      expect(component.descriptionCtrl.hasError('required')).toBe(true);
      component.descriptionCtrl.setValue('a'.repeat(2001));
      expect(component.descriptionCtrl.hasError('maxlength')).toBe(true);
    });

    it('rejects a maxBudget below 1', () => {
      const component = createComponent();
      component.budgetCtrl.setValue(0);
      expect(component.budgetCtrl.hasError('min')).toBe(true);
      component.budgetCtrl.setValue(1);
      expect(component.budgetCtrl.valid).toBe(true);
    });

    it('validates stlFileUrl must look like an http(s) url when provided', () => {
      const component = createComponent();
      component.stlUrlCtrl.setValue('not-a-url');
      expect(component.stlUrlCtrl.hasError('pattern')).toBe(true);
      component.stlUrlCtrl.setValue('https://example.com/model.stl');
      expect(component.stlUrlCtrl.valid).toBe(true);
    });

    it('is valid with required fields filled and defaults for the rest', () => {
      const component = createComponent();
      component.titleCtrl.setValue('My listing');
      component.descriptionCtrl.setValue('A description');
      expect(component.form.valid).toBe(true);
    });
  });

  describe('estimatePrice()', () => {
    it('does nothing when the description is empty/whitespace', () => {
      const component = createComponent();
      component.descriptionCtrl.setValue('   ');
      component.estimatePrice();
      expect(priceEstimateStub.getEstimate).not.toHaveBeenCalled();
    });

    it('calls the service with description/material/size/quality and stores the result', () => {
      const response: PriceEstimateResponse = {
        priceLow: 5, priceHigh: 10, reasoning: 'r', assumedWeightGrams: 20,
        assumedPrintHours: 1, warnings: [], aiGenerated: true
      };
      priceEstimateStub.getEstimate.mockReturnValue(of(response));
      const component = createComponent();
      component.descriptionCtrl.setValue('  A neat model  ');
      component.materialCtrl.setValue('PETG');
      component.estimatorSize.set('large');
      component.estimatorQuality.set('ultra');

      component.estimatePrice();

      expect(priceEstimateStub.getEstimate).toHaveBeenCalledWith({
        description: 'A neat model',
        material: 'PETG',
        size: 'large',
        quality: 'ultra'
      });
      expect(component.priceEstimate()).toEqual(response);
      expect(component.priceEstimateLoading()).toBe(false);
    });

    it('sets priceEstimateError on failure', () => {
      priceEstimateStub.getEstimate.mockReturnValue(throwError(() => new Error('fail')));
      const component = createComponent();
      component.descriptionCtrl.setValue('desc');

      component.estimatePrice();

      expect(component.priceEstimateError()).toBe('Nie udało się uzyskać wyceny. Spróbuj ponownie.');
      expect(component.priceEstimateLoading()).toBe(false);
    });
  });

  describe('file selection', () => {
    it('accepts allowed extensions and rejects others', () => {
      const component = createComponent();
      component.onFilesSelected({ target: { files: [fileOf('model.stl'), fileOf('notes.txt')], value: '' } } as unknown as Event);
      expect(component.selectedFiles().map(f => f.name)).toEqual(['model.stl']);
    });

    it('dedups files by name when added twice', () => {
      const component = createComponent();
      const f1 = fileOf('model.stl');
      component.onDropFiles({ preventDefault: vi.fn(), dataTransfer: { files: [f1] } } as unknown as DragEvent);
      component.onDropFiles({ preventDefault: vi.fn(), dataTransfer: { files: [f1] } } as unknown as DragEvent);
      expect(component.selectedFiles().length).toBe(1);
    });

    it('removeFile() removes only the matching file instance', () => {
      const component = createComponent();
      const f1 = fileOf('a.stl');
      const f2 = fileOf('b.obj');
      component.onDropFiles({ preventDefault: vi.fn(), dataTransfer: { files: [f1, f2] } } as unknown as DragEvent);
      component.removeFile(f1);
      expect(component.selectedFiles()).toEqual([f2]);
    });

    it('onDragOver()/onDragLeave() toggle isDragging', () => {
      const component = createComponent();
      component.onDragOver({ preventDefault: vi.fn() } as unknown as DragEvent);
      expect(component.isDragging()).toBe(true);
      component.onDragLeave({ currentTarget: { contains: () => false } } as unknown as DragEvent);
      expect(component.isDragging()).toBe(false);
    });

    it('fileIcon() maps image/obj/other extensions', () => {
      const component = createComponent();
      expect(component.fileIcon(fileOf('a.png'))).toBe('🖼️');
      expect(component.fileIcon(fileOf('a.obj'))).toBe('📦');
      expect(component.fileIcon(fileOf('a.stl'))).toBe('📄');
    });

    it('formatSize() formats bytes as KB below 1MB and MB above', () => {
      const component = createComponent();
      expect(component.formatSize(500)).toBe('0 KB');
      expect(component.formatSize(2048)).toBe('2 KB');
      expect(component.formatSize(2 * 1024 * 1024)).toBe('2.0 MB');
    });
  });

  describe('submit()', () => {
    it('marks the form touched and does not call the service when invalid', () => {
      const component = createComponent();
      component.submit();
      expect(listingStub.createListing).not.toHaveBeenCalled();
      expect(component.titleCtrl.touched).toBe(true);
    });

    it('creates the listing, omitting empty optional fields', () => {
      const created: Listing = { id: '1', title: 'T', description: 'D', requiredMaterial: 'PLA' };
      listingStub.createListing.mockReturnValue(of(created));
      const component = createComponent();
      component.titleCtrl.setValue('Test title');
      component.descriptionCtrl.setValue('D');

      component.submit();

      expect(listingStub.createListing).toHaveBeenCalledWith({
        title: 'Test title',
        description: 'D',
        requiredMaterial: 'PLA',
        estimatorSize: 'medium',
        estimatorQuality: 'normal'
      });
    });

    it('includes maxBudget and stlFileUrl when set', () => {
      const created: Listing = { id: '1', title: 'T', description: 'D', requiredMaterial: 'PLA' };
      listingStub.createListing.mockReturnValue(of(created));
      const component = createComponent();
      component.titleCtrl.setValue('Test title');
      component.descriptionCtrl.setValue('D');
      component.budgetCtrl.setValue(50);
      component.stlUrlCtrl.setValue('https://example.com/a.stl');

      component.submit();

      expect(listingStub.createListing).toHaveBeenCalledWith(expect.objectContaining({
        maxBudget: 50,
        stlFileUrl: 'https://example.com/a.stl'
      }));
    });

    it('uploads selected files after listing creation, then navigates after the success delay', () => {
      const created: Listing = { id: '1', title: 'T', description: 'D', requiredMaterial: 'PLA' };
      listingStub.createListing.mockReturnValue(of(created));
      listingStub.uploadStlFiles.mockReturnValue(of([]));
      const component = createComponent();
      component.titleCtrl.setValue('Test title');
      component.descriptionCtrl.setValue('D');
      component.onDropFiles({ preventDefault: vi.fn(), dataTransfer: { files: [fileOf('a.stl')] } } as unknown as DragEvent);

      component.submit();

      expect(listingStub.uploadStlFiles).toHaveBeenCalledWith('1', expect.arrayContaining([expect.objectContaining({ name: 'a.stl' })]));
      expect(component.success()).toBe(true);
      expect(router.navigate).not.toHaveBeenCalled();

      vi.advanceTimersByTime(2000);
      expect(router.navigate).toHaveBeenCalledWith(['/zlecenia']);
    });

    it('still finishes successfully if file upload fails (listing was already created)', () => {
      const created: Listing = { id: '1', title: 'T', description: 'D', requiredMaterial: 'PLA' };
      listingStub.createListing.mockReturnValue(of(created));
      listingStub.uploadStlFiles.mockReturnValue(throwError(() => new Error('upload failed')));
      const component = createComponent();
      component.titleCtrl.setValue('Test title');
      component.descriptionCtrl.setValue('D');
      component.onDropFiles({ preventDefault: vi.fn(), dataTransfer: { files: [fileOf('a.stl')] } } as unknown as DragEvent);

      component.submit();

      expect(component.success()).toBe(true);
      expect(component.loading()).toBe(false);
    });

    it('skips the upload call entirely when there are no selected files', () => {
      const created: Listing = { id: '1', title: 'T', description: 'D', requiredMaterial: 'PLA' };
      listingStub.createListing.mockReturnValue(of(created));
      const component = createComponent();
      component.titleCtrl.setValue('Test title');
      component.descriptionCtrl.setValue('D');

      component.submit();

      expect(listingStub.uploadStlFiles).not.toHaveBeenCalled();
      expect(component.success()).toBe(true);
    });

    it('sets serverError when listing creation fails', () => {
      listingStub.createListing.mockReturnValue(throwError(() => new Error('fail')));
      const component = createComponent();
      component.titleCtrl.setValue('Test title');
      component.descriptionCtrl.setValue('D');

      component.submit();

      expect(component.serverError()).toBe('Nie udało się dodać zlecenia. Sprawdź połączenie i spróbuj ponownie.');
      expect(component.loading()).toBe(false);
    });
  });
});
