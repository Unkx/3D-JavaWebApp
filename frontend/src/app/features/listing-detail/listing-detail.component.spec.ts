import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ListingDetailComponent } from './listing-detail.component';
import { ListingService, Listing, StlFile } from '../../services/listing.service';
import { OfferService, Offer } from '../../services/offer.service';
import { AuthService, AuthUser } from '../../services/auth.service';
import { ConversationService } from '../../services/conversation.service';
import { PriceEstimateService, PriceEstimateResponse } from '../../services/price-estimate.service';

describe('ListingDetailComponent', () => {
  let listingStub: {
    getListing: ReturnType<typeof vi.fn>;
    getStlFiles: ReturnType<typeof vi.fn>;
    deleteStlFile: ReturnType<typeof vi.fn>;
    deleteListing: ReturnType<typeof vi.fn>;
    patchListing: ReturnType<typeof vi.fn>;
    reorderStlFiles: ReturnType<typeof vi.fn>;
  };
  let offerStub: {
    getOffersForListing: ReturnType<typeof vi.fn>;
    createOffer: ReturnType<typeof vi.fn>;
    selectOffer: ReturnType<typeof vi.fn>;
  };
  let authStub: { currentUser: ReturnType<typeof vi.fn> };
  let conversationStub: { createOrGet: ReturnType<typeof vi.fn> };
  let priceEstimateStub: { getEstimate: ReturnType<typeof vi.fn> };
  let httpStub: { get: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  const baseListing: Listing = {
    id: 'l1', title: 'A model', description: 'A nice model', requiredMaterial: 'PLA',
    estimatorSize: 'medium', user: { id: 'owner1' }
  };

  function createComponent(id = 'l1'): ListingDetailComponent {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { paramMap: convertToParamMap({ id }) } }
    });
    return TestBed.runInInjectionContext(() => new ListingDetailComponent());
  }

  beforeEach(() => {
    vi.useFakeTimers();
    listingStub = {
      getListing: vi.fn().mockReturnValue(of(baseListing)),
      getStlFiles: vi.fn().mockReturnValue(of([])),
      deleteStlFile: vi.fn().mockReturnValue(of(undefined)),
      deleteListing: vi.fn().mockReturnValue(of(undefined)),
      patchListing: vi.fn(),
      reorderStlFiles: vi.fn().mockReturnValue(of(undefined))
    };
    offerStub = {
      getOffersForListing: vi.fn().mockReturnValue(of([])),
      createOffer: vi.fn(),
      selectOffer: vi.fn()
    };
    authStub = { currentUser: vi.fn().mockReturnValue(null) };
    conversationStub = { createOrGet: vi.fn() };
    priceEstimateStub = { getEstimate: vi.fn() };
    httpStub = { get: vi.fn() };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: ListingService, useValue: listingStub },
        { provide: OfferService, useValue: offerStub },
        { provide: AuthService, useValue: authStub },
        { provide: ConversationService, useValue: conversationStub },
        { provide: PriceEstimateService, useValue: priceEstimateStub },
        { provide: HttpClient, useValue: httpStub },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'l1' }) } } }
      ]
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('ngOnInit()', () => {
    it('loads listing, offers, and stl files for the route id', () => {
      const files: StlFile[] = [{ id: 'f1', fileName: 'a.stl', fileSize: 100, kind: 'stl', createdAt: '' }];
      listingStub.getStlFiles.mockReturnValue(of(files));
      const offers: Offer[] = [{ id: 'o1', price: 10, printingTimeHours: 1, filamentGrams: 5, printerModel: 'X' }];
      offerStub.getOffersForListing.mockReturnValue(of(offers));

      const component = createComponent('l1');
      component.ngOnInit();

      expect(listingStub.getListing).toHaveBeenCalledWith('l1');
      expect(offerStub.getOffersForListing).toHaveBeenCalledWith('l1');
      expect(listingStub.getStlFiles).toHaveBeenCalledWith('l1');
      expect(component.listing()).toEqual(baseListing);
      expect(component.offers()).toEqual(offers);
      expect(component.stlFiles()).toEqual(files);
      expect(component.selectedFileId()).toBe('f1');
      expect(component.listingLoading()).toBe(false);
      expect(component.offersLoading()).toBe(false);
    });

    it('sets listingError when the listing fails to load', () => {
      listingStub.getListing.mockReturnValue(throwError(() => new Error('404')));
      const component = createComponent();
      component.ngOnInit();
      expect(component.listingError()).toBe('Nie znaleziono zlecenia.');
      expect(component.listingLoading()).toBe(false);
    });

    it('adopts estimatorSize/Quality from the loaded listing', () => {
      listingStub.getListing.mockReturnValue(of({ ...baseListing, estimatorSize: 'large', estimatorQuality: 'ultra' }));
      const component = createComponent();
      component.ngOnInit();
      expect(component.estimatorSize()).toBe('large');
      expect(component.estimatorQuality()).toBe('ultra');
    });
  });

  describe('feeBreakdown computed', () => {
    it('computes fee (10%) and size-based shipping', () => {
      const component = createComponent();
      component.ngOnInit();
      const offer: Offer = { price: 100, printingTimeHours: 1, filamentGrams: 10, printerModel: 'X' };
      const result = component.feeBreakdown()(offer);
      expect(result.contractorPrice).toBe(100);
      expect(result.fee).toBe(10);
      expect(result.shipping).toBe(14.99); // medium
      expect(result.total).toBe(124.99);
    });

    it('uses small/large shipping rates based on the listing size', () => {
      listingStub.getListing.mockReturnValue(of({ ...baseListing, estimatorSize: 'small' }));
      const component = createComponent();
      component.ngOnInit();
      expect(component.feeBreakdown()({ price: 10, printingTimeHours: 1, filamentGrams: 1, printerModel: 'X' }).shipping).toBe(12.99);
    });
  });

  describe('permission computeds', () => {
    const user: AuthUser = { token: 't', email: 'a@b.com', role: 'USER', userId: 'owner1' };
    const adminUser: AuthUser = { token: 't', email: 'admin@b.com', role: 'ADMIN', userId: 'admin1' };

    it('canUploadFile/canManage is true only for the listing owner', () => {
      authStub.currentUser.mockReturnValue(user);
      const component = createComponent();
      component.ngOnInit();
      expect(component.canUploadFile()).toBe(true);
      expect(component.canManage()).toBe(true);
    });

    it('canUploadFile is false for a non-owner, non-admin', () => {
      authStub.currentUser.mockReturnValue({ ...user, userId: 'someone-else' });
      const component = createComponent();
      component.ngOnInit();
      expect(component.canUploadFile()).toBe(false);
    });

    it('canDelete is true for the owner or an admin', () => {
      authStub.currentUser.mockReturnValue(user);
      const component = createComponent();
      component.ngOnInit();
      expect(component.canDelete()).toBe(true);

      authStub.currentUser.mockReturnValue(adminUser);
      expect(component.canDelete()).toBe(true);
    });

    it('canDelete is false for a logged-out visitor', () => {
      authStub.currentUser.mockReturnValue(null);
      const component = createComponent();
      component.ngOnInit();
      expect(component.canDelete()).toBe(false);
    });

    it('isAdmin/isOwner reflect the current user role and id', () => {
      authStub.currentUser.mockReturnValue(adminUser);
      const component = createComponent();
      component.ngOnInit();
      expect(component.isAdmin()).toBe(true);
      expect(component.isOwner()).toBe(false);
    });
  });

  describe('estimatePrice()', () => {
    it('does nothing when the listing has no description', () => {
      listingStub.getListing.mockReturnValue(of({ ...baseListing, description: '' }));
      const component = createComponent();
      component.ngOnInit();
      component.estimatePrice();
      expect(priceEstimateStub.getEstimate).not.toHaveBeenCalled();
    });

    it('requests an estimate using the listing description/material', () => {
      const response: PriceEstimateResponse = {
        priceLow: 5, priceHigh: 10, reasoning: 'r', assumedWeightGrams: 20,
        assumedPrintHours: 1, warnings: [], aiGenerated: true
      };
      priceEstimateStub.getEstimate.mockReturnValue(of(response));
      const component = createComponent();
      component.ngOnInit();

      component.estimatePrice();

      expect(priceEstimateStub.getEstimate).toHaveBeenCalledWith({
        description: 'A nice model',
        material: 'PLA',
        size: 'medium',
        quality: 'normal'
      });
      expect(component.priceEstimate()).toEqual(response);
    });

    it('sets priceEstimateError on failure', () => {
      priceEstimateStub.getEstimate.mockReturnValue(throwError(() => new Error('fail')));
      const component = createComponent();
      component.ngOnInit();

      component.estimatePrice();

      expect(component.priceEstimateError()).toBe('Nie udało się uzyskać wyceny. Spróbuj ponownie.');
    });
  });

  describe('selectFile()', () => {
    it('switches the selected file and briefly hides/reshows the viewer', () => {
      const component = createComponent();
      component.ngOnInit();

      component.selectFile('f2');

      expect(component.selectedFileId()).toBe('f2');
      expect(component.viewerVisible()).toBe(false);

      vi.advanceTimersByTime(0);
      expect(component.viewerVisible()).toBe(true);
    });

    it('is a no-op when selecting the already-selected file', () => {
      const component = createComponent();
      component.ngOnInit();
      component.selectedFileId.set('f1');
      component.selectFile('f1');
      expect(component.viewerVisible()).toBe(true);
    });
  });

  describe('deleteFile()', () => {
    it('does nothing when the user cancels the confirm dialog', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      const component = createComponent();
      component.ngOnInit();

      component.deleteFile('f1', { stopPropagation: vi.fn() } as unknown as Event);

      expect(listingStub.deleteStlFile).not.toHaveBeenCalled();
    });

    it('deletes the file and reloads the list when confirmed', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const files: StlFile[] = [{ id: 'f1', fileName: 'a.stl', fileSize: 100, kind: 'stl', createdAt: '' }];
      // First load (ngOnInit) returns the file; the reload after deletion returns an empty
      // list, simulating the backend no longer serving the deleted file.
      listingStub.getStlFiles.mockReturnValueOnce(of(files)).mockReturnValue(of([]));
      const component = createComponent();
      component.ngOnInit();
      component.selectedFileId.set('f1');

      component.deleteFile('f1', { stopPropagation: vi.fn() } as unknown as Event);

      expect(listingStub.deleteStlFile).toHaveBeenCalledWith('l1', 'f1');
      expect(component.selectedFileId()).toBeNull();
      expect(listingStub.getStlFiles).toHaveBeenCalledTimes(2);
    });
  });

  describe('inline edit', () => {
    it('openEdit() populates the edit form from the current listing', () => {
      listingStub.getListing.mockReturnValue(of({ ...baseListing, maxBudget: 42, estimatorQuality: 'ultra' }));
      const component = createComponent();
      component.ngOnInit();

      component.openEdit();

      expect(component.editMode()).toBe(true);
      expect(component.editForm.value.description).toBe('A nice model');
      expect(component.editForm.value.maxBudget).toBe(42);
      expect(component.editQuality()).toBe('ultra');
    });

    it('cancelEdit() exits edit mode', () => {
      const component = createComponent();
      component.ngOnInit();
      component.openEdit();
      component.cancelEdit();
      expect(component.editMode()).toBe(false);
    });

    it('saveEdit() marks touched and does not call the API when invalid', () => {
      const component = createComponent();
      component.ngOnInit();
      component.openEdit();
      component.editForm.controls.requiredMaterial.setValue('');

      component.saveEdit();

      expect(listingStub.patchListing).not.toHaveBeenCalled();
      expect(component.editForm.controls.requiredMaterial.touched).toBe(true);
    });

    it('saveEdit() patches the listing and exits edit mode on success', () => {
      const updated: Listing = { ...baseListing, description: 'Updated' };
      listingStub.patchListing.mockReturnValue(of(updated));
      const component = createComponent();
      component.ngOnInit();
      component.openEdit();
      component.editForm.controls.description.setValue('Updated');

      component.saveEdit();

      expect(listingStub.patchListing).toHaveBeenCalledWith('l1', expect.objectContaining({ description: 'Updated' }));
      expect(component.listing()).toEqual(updated);
      expect(component.editMode()).toBe(false);
    });

    it('saveEdit() sets editError on failure', () => {
      listingStub.patchListing.mockReturnValue(throwError(() => new Error('fail')));
      const component = createComponent();
      component.ngOnInit();
      component.openEdit();

      component.saveEdit();

      expect(component.editError()).toBe('Nie udało się zapisać zmian. Spróbuj ponownie.');
      expect(component.editSaving()).toBe(false);
    });
  });

  describe('drag reorder', () => {
    it('reorders stlFiles and persists the new order', () => {
      const files: StlFile[] = [
        { id: 'a', fileName: 'a.stl', fileSize: 1, kind: 'stl', createdAt: '' },
        { id: 'b', fileName: 'b.stl', fileSize: 1, kind: 'stl', createdAt: '' }
      ];
      listingStub.getStlFiles.mockReturnValue(of(files));
      const component = createComponent();
      component.ngOnInit();

      component.onDragStart('b');
      component.onDrop({ preventDefault: vi.fn() } as unknown as DragEvent, 'a');

      expect(component.stlFiles().map(f => f.id)).toEqual(['b', 'a']);
      expect(listingStub.reorderStlFiles).toHaveBeenCalledWith('l1', ['b', 'a']);
    });

    it('does nothing when dropping onto itself', () => {
      const component = createComponent();
      component.ngOnInit();
      component.onDragStart('a');
      component.onDrop({ preventDefault: vi.fn() } as unknown as DragEvent, 'a');
      expect(listingStub.reorderStlFiles).not.toHaveBeenCalled();
    });
  });

  describe('submitOffer()', () => {
    it('marks the form touched and does not submit when invalid', () => {
      const component = createComponent();
      component.ngOnInit();
      component.submitOffer();
      expect(offerStub.createOffer).not.toHaveBeenCalled();
      expect(component.priceCtrl.touched).toBe(true);
    });

    it('submits the offer, appends it to the list, and clears submitSuccess after a delay', () => {
      const newOffer: Offer = { id: 'o1', price: 20, printingTimeHours: 2, filamentGrams: 10, printerModel: 'X' };
      offerStub.createOffer.mockReturnValue(of(newOffer));
      const component = createComponent();
      component.ngOnInit();
      component.priceCtrl.setValue(20);
      component.timeCtrl.setValue(2);
      component.filamentCtrl.setValue(10);
      component.printerCtrl.setValue('Ender 3');

      component.submitOffer();

      expect(component.offers()).toEqual([newOffer]);
      expect(component.submitSuccess()).toBe(true);

      vi.advanceTimersByTime(4000);
      expect(component.submitSuccess()).toBe(false);
    });

    it('sets submitError on failure', () => {
      offerStub.createOffer.mockReturnValue(throwError(() => new Error('fail')));
      const component = createComponent();
      component.ngOnInit();
      component.priceCtrl.setValue(20);
      component.timeCtrl.setValue(2);
      component.filamentCtrl.setValue(10);
      component.printerCtrl.setValue('Ender 3');

      component.submitOffer();

      expect(component.submitError()).toBe('Nie udało się złożyć oferty. Spróbuj ponownie.');
    });
  });

  describe('checkout flow', () => {
    it('openCheckout()/cancelCheckout() toggle the checkout offer id', () => {
      const component = createComponent();
      component.ngOnInit();
      component.openCheckout({ id: 'o1', price: 1, printingTimeHours: 1, filamentGrams: 1, printerModel: 'X' });
      expect(component.checkoutOfferId()).toBe('o1');
      component.cancelCheckout();
      expect(component.checkoutOfferId()).toBeNull();
    });

    it('confirmCheckout() selects the offer and reloads listing/offers on success', () => {
      offerStub.selectOffer.mockReturnValue(of({}));
      const component = createComponent();
      component.ngOnInit();
      component.checkoutPaczkomat.set('KRA01');

      component.confirmCheckout({ id: 'o1', price: 1, printingTimeHours: 1, filamentGrams: 1, printerModel: 'X' });

      expect(offerStub.selectOffer).toHaveBeenCalledWith('o1', 'KRA01');
      expect(component.checkoutPaying()).toBe(false);
      expect(component.checkoutOfferId()).toBeNull();
      expect(listingStub.getListing).toHaveBeenCalledTimes(2);
    });

    it('confirmCheckout() sets acceptError on failure', () => {
      offerStub.selectOffer.mockReturnValue(throwError(() => new Error('fail')));
      const component = createComponent();
      component.ngOnInit();

      component.confirmCheckout({ id: 'o1', price: 1, printingTimeHours: 1, filamentGrams: 1, printerModel: 'X' });

      expect(component.acceptError()).toBe('Nie udało się dokonać płatności.');
    });
  });

  describe('openMessage()', () => {
    it('navigates to the conversation on success', () => {
      conversationStub.createOrGet.mockReturnValue(of({ id: 'conv1' }));
      const component = createComponent();
      component.ngOnInit();

      component.openMessage({ id: 'o1', price: 1, printingTimeHours: 1, filamentGrams: 1, printerModel: 'X', user: { id: 'u2' } });

      expect(conversationStub.createOrGet).toHaveBeenCalledWith('l1', 'u2');
      expect(router.navigate).toHaveBeenCalledWith(['/wiadomosci'], { queryParams: { conv: 'conv1' } });
    });

    it('alerts on failure', () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      conversationStub.createOrGet.mockReturnValue(throwError(() => new Error('fail')));
      const component = createComponent();
      component.ngOnInit();

      component.openMessage({ id: 'o1', price: 1, printingTimeHours: 1, filamentGrams: 1, printerModel: 'X' });

      expect(alertSpy).toHaveBeenCalled();
    });
  });

  describe('pure helpers', () => {
    it('estimateCost() and priceVsEstimate() classify pricing', () => {
      const component = createComponent();
      const cheapOffer: Offer = { price: 1, printingTimeHours: 1, filamentGrams: 10, printerModel: 'X' };
      const { low, high } = component.estimateCost(cheapOffer);
      expect(low).toBeGreaterThan(0);
      expect(high).toBeGreaterThan(low);
      expect(component.priceVsEstimate(cheapOffer)).toBe('cheap');

      const expensiveOffer: Offer = { price: 100000, printingTimeHours: 1, filamentGrams: 10, printerModel: 'X' };
      expect(component.priceVsEstimate(expensiveOffer)).toBe('expensive');
    });

    it('sizeLabel()/qualityLabel()/statusLabel() map known values and pass through unknown ones', () => {
      const component = createComponent();
      expect(component.sizeLabel('small')).toBe('Mały');
      expect(component.sizeLabel('unknown')).toBe('unknown');
      expect(component.qualityLabel('ultra')).toBe('Ultra');
      expect(component.statusLabel('OPEN')).toBe('Otwarte');
      expect(component.statusLabel(undefined)).toBe('');
    });
  });

  describe('downloadZip()', () => {
    it('fetches the zip blob and triggers a download', () => {
      const blob = new Blob(['data']);
      httpStub.get.mockReturnValue(of(blob));
      const createObjectURL = vi.fn().mockReturnValue('blob:fake');
      const revokeObjectURL = vi.fn();
      (URL as unknown as { createObjectURL: unknown }).createObjectURL = createObjectURL;
      (URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = revokeObjectURL;
      const clickSpy = vi.fn();
      const fakeAnchor = { href: '', download: '', click: clickSpy } as unknown as HTMLAnchorElement;
      const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(fakeAnchor);

      const component = createComponent();
      component.ngOnInit();

      component.downloadZip();

      expect(httpStub.get).toHaveBeenCalledWith('/api/listings/l1/download-zip', { responseType: 'blob' });
      expect(clickSpy).toHaveBeenCalled();
      expect(component.downloading()).toBe(false);
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:fake');

      createElementSpy.mockRestore();
    });

    it('resets downloading on failure', () => {
      httpStub.get.mockReturnValue(throwError(() => new Error('fail')));
      const component = createComponent();
      component.ngOnInit();

      component.downloadZip();

      expect(component.downloading()).toBe(false);
    });
  });
});
