import { api } from '../api.js';
import { toast } from '../toast.js';
import {
  formatCurrency,
  formatDate,
  stampClass,
  humanizeReason,
  escapeHtml,
  displayStatus
} from '../utils.js';

import {
  buildPaymentPayload,
  generatePrn,
  getBankConstants
} from '../crypto.js';


/* ============================================================
   PAGE ELEMENTS
   ============================================================ */

const form =
    document.getElementById('paymentForm');

const amountInput =
    document.getElementById('amountInput');

const merchantNoteInput =
    document.getElementById('merchantNote');

const payBtn =
    document.getElementById('payBtn');


/* ============================================================
   PAYMENT MODAL
   ============================================================ */

const paymentModalBackdrop =
    document.getElementById('paymentModalBackdrop');

const paymentModalClose =
    document.getElementById('paymentModalClose');

const modalStepMethod =
    document.getElementById('modalStepMethod');

const modalStepAccount =
    document.getElementById('modalStepAccount');

const modalStepProcessing =
    document.getElementById('modalStepProcessing');

const modalStepResult =
    document.getElementById('modalStepResult');


const continueNetbankingBtn =
    document.getElementById(
        'continueNetbankingBtn'
    );


const accountGrid =
    document.getElementById(
        'accountGrid'
    );


const callbackBehaviorSelect =
    document.getElementById(
        'callbackBehaviorSelect'
    );


const modalAmount =
    document.getElementById(
        'modalAmount'
    );


const modalMerchant =
    document.getElementById(
        'modalMerchant'
    );


const accountModalAmount =
    document.getElementById(
        'accountModalAmount'
    );


const accountModalPrn =
    document.getElementById(
        'accountModalPrn'
    );


const processingMessage =
    document.getElementById(
        'processingMessage'
    );


const processingStepOne =
    document.getElementById(
        'processingStepOne'
    );


const processingStepTwo =
    document.getElementById(
        'processingStepTwo'
    );


const processingStepThree =
    document.getElementById(
        'processingStepThree'
    );


const receiptCard =
    document.getElementById(
        'receiptCard'
    );


const newPaymentBtn =
    document.getElementById(
        'newPaymentBtn'
    );


/* ============================================================
   STATE
   ============================================================ */

let currentPrn = null;

let currentAmount = null;

let currentMerchant =
    'BillDeskTestMerchantName';


const RETURN_URL =
    'http://localhost:8082/billdesk/callback-receiver';


/* ============================================================
   BUTTON LOADING
   ============================================================ */

function setLoading(
    button,
    loading
) {

  if (!button) {
    return;
  }

  button.disabled = loading;

  const label =
      button.querySelector(
          '.btn-label'
      );

  const spinner =
      button.querySelector(
          '.spinner'
      );

  if (label) {
    label.style.opacity =
        loading ? '0' : '1';
  }

  if (spinner) {
    spinner.hidden =
        !loading;
  }
}


/* ============================================================
   MODAL HELPERS
   ============================================================ */

function showModalStep(step) {

  [
    modalStepMethod,
    modalStepAccount,
    modalStepProcessing,
    modalStepResult
  ].forEach((element) => {

    element.classList.add(
        'hidden'
    );

  });

  step.classList.remove(
      'hidden'
  );
}


function openPaymentModal() {

  paymentModalBackdrop.classList.remove(
      'hidden'
  );

  document.body.classList.add(
      'modal-open'
  );

  showModalStep(
      modalStepMethod
  );
}


function closePaymentModal() {

  paymentModalBackdrop.classList.add(
      'hidden'
  );

  document.body.classList.remove(
      'modal-open'
  );
}


function resetModal() {

  showModalStep(
      modalStepMethod
  );

  processingStepOne.classList.add(
      'active'
  );

  processingStepTwo.classList.remove(
      'active'
  );

  processingStepThree.classList.remove(
      'active'
  );

  processingMessage.textContent =
      'Connecting securely to Dhanlaxmi Bank…';

  accountGrid.innerHTML = '';

  callbackBehaviorSelect.value = '';

}


/* ============================================================
   RESET PAYMENT PAGE
   ============================================================ */

function resetFlow() {

  form.reset();

  currentPrn = null;

  currentAmount = null;

  currentMerchant =
      'BillDeskTestMerchantName';

  resetModal();

  closePaymentModal();
}


/* ============================================================
   MAIN PAYMENT FORM
   ============================================================ */

form.addEventListener(
    'submit',
    async (event) => {

      event.preventDefault();

      const amount =
          Number(
              amountInput.value
          );

      if (!amount || amount <= 0) {

        toast.error(
            'Enter a valid payment amount.'
        );

        return;
      }


      setLoading(
          payBtn,
          true
      );


      try {

        currentAmount =
            amount;

        currentMerchant =
            merchantNoteInput.value.trim()
            || 'BillDeskTestMerchantName';


        /*
         * Generate PRN exactly like the
         * previous frontend flow.
         */
        const prn =
            generatePrn();


        /*
         * Build the existing encrypted
         * payment request.
         */
        const payload =
            await buildPaymentPayload({
              prn,
              amount:
                  amount.toFixed(2),
              merchantName:
              currentMerchant,
              returnUrl:
              RETURN_URL,
            });


        /*
         * Existing backend endpoint.
         * NOTHING in backend flow is changed.
         */
        const response =
            await api.submitPaymentRequest(
                payload.mercode,
                payload.encDhanBankData
            );


        currentPrn =
            response.prn;


        /*
         * Fill popup summary.
         */
        modalAmount.textContent =
            formatCurrency(
                currentAmount
            );

        modalMerchant.textContent =
            currentMerchant;

        accountModalAmount.textContent =
            formatCurrency(
                currentAmount
            );

        accountModalPrn.textContent =
            currentPrn;


        toast.success(
            'Payment request accepted.'
        );


        /*
         * Open realistic bank popup.
         */
        openPaymentModal();


      } catch (error) {

        toast.error(
            `Payment request failed: ${error.message}`
        );

      } finally {

        setLoading(
            payBtn,
            false
        );

      }

    }
);


/* ============================================================
   STEP 1 → STEP 2
   NET BANKING
   ============================================================ */

continueNetbankingBtn.addEventListener(
    'click',
    async () => {

      showModalStep(
          modalStepAccount
      );

      await loadAccounts();

    }
);


/* ============================================================
   LOAD ACCOUNTS
   ============================================================ */

async function loadAccounts() {

  accountGrid.innerHTML = `
    <div class="account-loading">

      <div class="account-loading-spinner"></div>

      <span>
        Loading bank accounts…
      </span>

    </div>
  `;


  try {

    const accounts =
        await api.listAccounts();


    if (!accounts ||
        accounts.length === 0) {

      accountGrid.innerHTML = `
        <div class="empty-state">
          <div class="empty-state-title">
            No test accounts available
          </div>

          <div class="empty-state-sub">
            Please check your DataSeeder.
          </div>
        </div>
      `;

      return;
    }


    accountGrid.innerHTML =
        accounts.map(
            (account) => {

              return `
            <button
              type="button"
              class="account-card"
              data-account="${escapeHtml(account.accountNo)}"
            >

              <div class="account-card-left">

                <div class="account-bank-mini">
                  D
                </div>

                <div>

                  <div class="account-name">
                    ${escapeHtml(account.holderName)}
                  </div>

                  <div class="account-no">
                    •••• •••• ${escapeHtml(
                  account.accountNo.slice(-4)
              )}
                  </div>

                </div>

              </div>


              <div class="account-card-right">

                <span class="account-balance-label">
                  Available balance
                </span>

                <span class="account-balance">
                  ${formatCurrency(
                  account.balance
              )}
                </span>

              </div>

              <div class="account-arrow">
                →
              </div>

            </button>
          `;

            }
        ).join('')
        + `
        <button
          type="button"
          class="account-card"
          data-account="0000000000"
        >

          <div class="account-card-left">

            <div class="account-bank-mini">
              ?
            </div>

            <div>

              <div class="account-name">
                Unregistered Account
              </div>

              <div class="account-no">
                0000 0000 00
              </div>

            </div>

          </div>

          <div class="account-card-right">

            <span class="account-balance-label">
              Test Scenario
            </span>

            <span class="account-balance invalid-balance">
              Invalid Account
            </span>

          </div>

          <div class="account-arrow">
            →
          </div>

        </button>
      `;


    accountGrid
        .querySelectorAll(
            '.account-card'
        )
        .forEach(
            (card) => {

              card.addEventListener(
                  'click',
                  () => {

                    authorizePayment(
                        card.dataset.account,
                        card
                    );

                  }
              );

            }
        );


  } catch (error) {

    accountGrid.innerHTML = `
      <div class="empty-state">

        <div class="empty-state-title">
          Unable to load accounts
        </div>

        <div class="empty-state-sub">
          ${escapeHtml(
        error.message
    )}
        </div>

      </div>
    `;

    toast.error(
        `Could not load accounts: ${error.message}`
    );

  }

}


/* ============================================================
   ACCOUNT SELECTED
   ============================================================ */

async function authorizePayment(
    accountNo,
    cardElement
) {

  /*
   * Prevent multiple clicks.
   */
  document
      .querySelectorAll(
          '.account-card'
      )
      .forEach(
          (card) => {

            card.disabled = true;

            card.classList.add(
                'disabled-account'
            );

          }
      );


  cardElement.classList.add(
      'selected-account'
  );


  /*
   * Show bank-style loading screen.
   */
  showModalStep(
      modalStepProcessing
  );


  /*
   * Change progress text during
   * the 2–3 second simulated flow.
   */
  const loadingTimers = [

    window.setTimeout(
        () => {

          processingStepTwo.classList.add(
              'active'
          );

          processingMessage.textContent =
              'Your bank is securely processing the transaction…';

        },
        650
    ),


    window.setTimeout(
        () => {

          processingStepThree.classList.add(
              'active'
          );

          processingMessage.textContent =
              'Waiting for payment confirmation…';

        },
        1400
    )

  ];


  const startedAt =
      Date.now();


  try {

    const behavior =
        callbackBehaviorSelect.value
        || undefined;


    /*
     * IMPORTANT:
     *
     * This is still the original backend API.
     *
     * Backend callback and automatic
     * Double Verification remain unchanged.
     */
    const result =
        await api.completePayment(
            currentPrn,
            accountNo,
            behavior
        );


    /*
     * Make sure the loading state stays
     * visible for at least 2.3 seconds.
     *
     * This makes the bank simulator feel
     * like a real payment gateway.
     */
    const minimumLoadingTime =
        2300;

    const elapsed =
        Date.now() - startedAt;

    const remaining =
        Math.max(
            0,
            minimumLoadingTime - elapsed
        );


    if (remaining > 0) {

      await new Promise(
          (resolve) =>
              window.setTimeout(
                  resolve,
                  remaining
              )
      );

    }


    loadingTimers.forEach(
        (timer) =>
            window.clearTimeout(timer)
    );


    processingMessage.textContent =
        'Payment confirmed.';


    renderReceipt(
        result
    );


    showModalStep(
        modalStepResult
    );


  } catch (error) {

    loadingTimers.forEach(
        (timer) =>
            window.clearTimeout(timer)
    );


    toast.error(
        `Could not complete payment: ${error.message}`
    );


    showModalStep(
        modalStepAccount
    );


    document
        .querySelectorAll(
            '.account-card'
        )
        .forEach(
            (card) => {

              card.disabled = false;

              card.classList.remove(
                  'disabled-account'
              );

            }
        );

  }

}


/* ============================================================
   PAYMENT RESULT
   ============================================================ */

function renderReceipt(
    result
) {

  const stamp =
      stampClass(
          result.status
      );

  const label =
      displayStatus(
          result.status
      );


  const isSuccess =
      result.status === 'SUCCESS';


  receiptCard.innerHTML = `

    <div class="receipt-top">

      <div class="receipt-bank-mark">
        DB
      </div>

      <div class="receipt-bank-name">
        Dhanlaxmi Bank
      </div>

    </div>


    <div class="receipt-title">

      ${
      isSuccess
          ? 'Payment Successful'
          : 'Payment Result'
  }

    </div>


    <div class="stamp ${stamp}">
      <span>
        ${label}
      </span>
    </div>


    <div class="receipt-amount">

      ${formatCurrency(
      currentAmount
  )}

    </div>


    ${
      result.failureReason
          ? `
          <div class="receipt-reason">

            ${humanizeReason(
              result.failureReason
          )}

          </div>
        `
          : ''
  }


    <div class="receipt-status-message">

      ${
      isSuccess
          ? 'Your payment has been processed successfully.'
          : 'The payment was processed by the simulator.'
  }

    </div>


    <div class="receipt-table">

      <div class="receipt-row">

        <span class="k">
          PRN
        </span>

        <span class="v">
          ${escapeHtml(
      result.prn
  )}
        </span>

      </div>


      <div class="receipt-row">

        <span class="k">
          Account
        </span>

        <span class="v">
          ${escapeHtml(
      result.accountNo
  )}
        </span>

      </div>


      <div class="receipt-row">

        <span class="k">
          Payment method
        </span>

        <span class="v">
          Net Banking
        </span>

      </div>


      <div class="receipt-row">

        <span class="k">
          Callback status
        </span>

        <span class="v">
          ${escapeHtml(
      result.callbackStatus
  )}
        </span>

      </div>


      <div class="receipt-row">

        <span class="k">
          Time
        </span>

        <span class="v">
          ${formatDate(
      new Date().toISOString()
  )}
        </span>

      </div>

    </div>

  `;

}


/* ============================================================
   MAKE ANOTHER PAYMENT
   ============================================================ */

newPaymentBtn.addEventListener(
    'click',
    () => {

      resetFlow();

    }
);


/* ============================================================
   CLOSE POPUP
   ============================================================ */

paymentModalClose.addEventListener(
    'click',
    () => {

      closePaymentModal();

    }
);


/*
 * Clicking outside popup closes it only
 * before processing/result.
 */
paymentModalBackdrop.addEventListener(
    'click',
    (event) => {

      if (
          event.target !==
          paymentModalBackdrop
      ) {

        return;

      }


      if (
          !modalStepProcessing.classList.contains(
              'hidden'
          )
          &&
          modalStepResult.classList.contains(
              'hidden'
          )
      ) {

        closePaymentModal();

      }

    }
);


/*
 * Escape key.
 */
document.addEventListener(
    'keydown',
    (event) => {

      if (
          event.key === 'Escape'
          &&
          !paymentModalBackdrop.classList.contains(
              'hidden'
          )
      ) {

        if (
            modalStepProcessing.classList.contains(
                'hidden'
            )
        ) {

          closePaymentModal();

        }

      }

    }
);


/* ============================================================
   ROUTER ENTRY
   ============================================================ */

export function renderPayment() {

  /*
   * Don't reset the current payment if
   * the page is being rendered normally.
   */
  if (!currentPrn) {

    form.reset();

  }

}