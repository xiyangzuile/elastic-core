/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

import java.io.IOException;

public abstract class NxtException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8278126418545304279L;

	public static class AccountControlException extends NotCurrentlyValidException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -3158698982508130693L;

		public AccountControlException(final String message) {
			super(message);
		}

		public AccountControlException(final String message, final Throwable cause) {
			super(message, cause);
		}

	}

	public static class ExistingTransactionException extends NotCurrentlyValidException {

		/**
		 * 
		 */
		private static final long serialVersionUID = 238633582217656708L;

		public ExistingTransactionException(final String message) {
			super(message);
		}

	}

	public static class InsufficientBalanceException extends NotCurrentlyValidException {

		/**
		 * 
		 */
		private static final long serialVersionUID = 3892830943717809186L;

		public InsufficientBalanceException(final String message) {
			super(message);
		}

		public InsufficientBalanceException(final String message, final Throwable cause) {
			super(message, cause);
		}

	}

	public static class LostValidityException extends ValidationException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -4435913410989969624L;

		public LostValidityException(final String message) {
			super(message);
		}

		public LostValidityException(final String message, final Throwable cause) {
			super(message, cause);
		}

	}

	public static class NotCurrentlyValidException extends ValidationException {

		/**
		 * 
		 */
		private static final long serialVersionUID = 8488904506080293440L;

		public NotCurrentlyValidException(final String message) {
			super(message);
		}

		public NotCurrentlyValidException(final String message, final Throwable cause) {
			super(message, cause);
		}

	}

	public static final class NotValidException extends ValidationException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -5065486096946575621L;

		public NotValidException(final String message) {
			super(message);
		}

		public NotValidException(final String message, final Throwable cause) {
			super(message, cause);
		}

	}

	public static final class NotYetEnabledException extends NotCurrentlyValidException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -7711689101554355302L;

		public NotYetEnabledException(final String message) {
			super(message);
		}

		public NotYetEnabledException(final String message, final Throwable throwable) {
			super(message, throwable);
		}

	}

	public static final class NotYetEncryptedException extends IllegalStateException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -3906380175077193938L;

		public NotYetEncryptedException(final String message) {
			super(message);
		}

		public NotYetEncryptedException(final String message, final Throwable cause) {
			super(message, cause);
		}

	}

	public static final class NxtIOException extends IOException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -3874359379672304539L;

		public NxtIOException(final String message) {
			super(message);
		}

		public NxtIOException(final String message, final Throwable cause) {
			super(message, cause);
		}

	}

	public static final class StopException extends RuntimeException {

		/**
		 * 
		 */
		private static final long serialVersionUID = 4476063168043141203L;

		public StopException(final String message) {
			super(message);
		}

		public StopException(final String message, final Throwable cause) {
			super(message, cause);
		}

	}

	public static abstract class ValidationException extends NxtException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -419444228963022363L;

		private ValidationException(final String message) {
			super(message);
		}

		private ValidationException(final String message, final Throwable cause) {
			super(message, cause);
		}

	}

	protected NxtException() {
		super();
	}

	protected NxtException(final String message) {
		super(message);
	}

	protected NxtException(final String message, final Throwable cause) {
		super(message, cause);
	}

	protected NxtException(final Throwable cause) {
		super(cause);
	}

}
