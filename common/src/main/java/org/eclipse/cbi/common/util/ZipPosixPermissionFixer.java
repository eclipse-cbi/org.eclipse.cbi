/*******************************************************************************
 * Copyright (c) 2016 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.common.util;

import static org.eclipse.cbi.common.util.RecordDefinition.createLEField;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;

import org.eclipse.cbi.common.util.RecordDefinition.Field;
import org.eclipse.cbi.common.util.ZipPosixPermissionFixer.CentralDirectoryHeader.Platform;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

abstract class ZipPosixPermissionFixer {

	private final ZipReader zipReader;

	public ZipPosixPermissionFixer(ZipReader zipReader) {
		this.zipReader = zipReader;
	}

	public void fixEntries() throws IOException {
		long firstCentralDireactoryHeader = firstCentralDirectoryHeaderPosition();

		Optional<CentralDirectoryHeader> directoryHeader = zipReader.readCentralDirectoryHeader(firstCentralDireactoryHeader);

		if (!directoryHeader.isPresent()) {
			if (!zipReader.readLocalFileHeader(0).isPresent()) {
				throw new ZipException("Not a zip archive");
			} else {
				throw new ZipException("Corrupted archive");
			}
		} else {
			do {
				if (directoryHeader.get().platform() == Platform.UNIX) {
					fixEntry(directoryHeader.get().filename(), directoryHeader.get().posixPermissions());
				}
				long nextDirectoryHeaderPosition = zipReader.position(directoryHeader.get()) + directoryHeader.get().size();
				directoryHeader = zipReader.readCentralDirectoryHeader(nextDirectoryHeaderPosition);
			} while (directoryHeader.isPresent());
		}
		
	}

	protected abstract void fixEntry(String entryName, Set<PosixFilePermission> posixPermissions) throws IOException;

	private long firstCentralDirectoryHeaderPosition() throws IOException {
		final EndOfCentralDirectory eocdrp = findEndOfCentralDirectory();
		
		if (zipReader.position(eocdrp) > Zip64EndOfCentralDirectoryLocator.DEFINITION.size()) {
			return firstDirectoryHeaderPositionZ64(eocdrp);
		} else {
			return eocdrp.offsetOfStartOfCentralDirectoryWithRespectToTheStartDiskNumber().longValue();
		}
	}

	private long firstDirectoryHeaderPositionZ64(final EndOfCentralDirectory eocdrp) throws IOException, ZipException {
		final long z64eocdrp = zipReader.position(eocdrp) - Zip64EndOfCentralDirectoryLocator.DEFINITION.size();
		Optional<Zip64EndOfCentralDirectoryLocator> z64eocdl = zipReader.readZip64EndOfCentralDirectoryLocator(z64eocdrp);
		if (z64eocdl.isPresent()) {
			Optional<Zip64EndOfCentralDirectory> zip64eocd = zipReader.readZip64EndOfCentralDirectory(z64eocdl.get().relativeOffsetOfTheZip64EndOfCentralDirectoryRecord().longValue());
			if (!zip64eocd.isPresent()) {
				throw new ZipException("Can't find Zip64 end of central directory");
			}
			return zip64eocd.get().offsetOfStartOfCentralDirectoryWithRespectToTheStartingDiskNumber();
		} else {
			return eocdrp.offsetOfStartOfCentralDirectoryWithRespectToTheStartDiskNumber().longValue();
		}
	}

	private EndOfCentralDirectory findEndOfCentralDirectory() throws IOException {
		final long endPosition = Math.max(0L, zipReader.zipSize() - EndOfCentralDirectory.MAX_SIZE);
		final long startPosition = zipReader.zipSize() - EndOfCentralDirectory.MIN_SIZE;
		
		Optional<EndOfCentralDirectory> endOfCentralDirectory;
		for (long positionToCheck = startPosition; positionToCheck >= endPosition; positionToCheck--) {
			endOfCentralDirectory = zipReader.readEndOfCentralDirectory(positionToCheck);
			if (endOfCentralDirectory.isPresent()) {
				return endOfCentralDirectory.get();
			}
		}

		throw new ZipException("End of central directory record not found");
	}

	static final class ZipReader {
		
		private final Map<Record, Long> recordPositions;
		private final SeekableByteChannelRecordReader reader;
		private final long zipSize;
		
		public ZipReader(SeekableByteChannelRecordReader reader, long zipSize) {
			this.reader = Preconditions.checkNotNull(reader);
			this.recordPositions = new HashMap<>();
			this.zipSize = zipSize;
		}
		
		public long zipSize() {
			return zipSize;
		}
		
		/**
		 * Checks whether the archive starts with a LFH. If it doesn't, it may be an
		 * empty archive.
		 */
		@SuppressWarnings("unchecked")
		public Optional<LocalFileHeader> readLocalFileHeader(long position) throws IOException {
			return (Optional<LocalFileHeader>) readRecord(LocalFileHeader.DEFINITION, position);
		}
		
		@SuppressWarnings("unchecked")
		public Optional<CentralDirectoryHeader> readCentralDirectoryHeader(long position) throws IOException {
			return (Optional<CentralDirectoryHeader>)readRecord(CentralDirectoryHeader.DEFINITION, position);
		}
		
		@SuppressWarnings("unchecked")
		public Optional<EndOfCentralDirectory> readEndOfCentralDirectory(long position) throws IOException {
			return (Optional<EndOfCentralDirectory>)readRecord(EndOfCentralDirectory.DEFINITION, position);
		}
		
		@SuppressWarnings("unchecked")
		public Optional<Zip64EndOfCentralDirectoryLocator> readZip64EndOfCentralDirectoryLocator(long position) throws IOException {
			return (Optional<Zip64EndOfCentralDirectoryLocator>)readRecord(Zip64EndOfCentralDirectoryLocator.DEFINITION, position);
		}
		
		@SuppressWarnings("unchecked")
		public Optional<Zip64EndOfCentralDirectory> readZip64EndOfCentralDirectory(long position) throws IOException {
			return (Optional<Zip64EndOfCentralDirectory>)readRecord(Zip64EndOfCentralDirectory.DEFINITION, position);
		}
		
		private Optional<? extends Record> readRecord(RecordDefinition rd, long position) throws IOException {
			final Optional<Record> ret;
			if (rd.signatureField().isPresent()) {
				ret = readRecordWithSignature(rd, position);
			} else {
				ret = doReadRecord(rd.recordClass(), rd, position);
			}
			return ret;
		}

		private Optional<Record> readRecordWithSignature(RecordDefinition rd, long position) throws IOException {
			final Optional<Record> ret;
			// all record signature in zips are uint32
			long signature = reader.uint32(rd.signatureField().get(), rd, position).longValue();
			if (signature == rd.signature()) {
				ret = doReadRecord(rd.recordClass(), rd, position);
			} else {
				ret = Optional.absent();
			}
			return ret;
		}

		private Optional<Record> doReadRecord(Class<? extends Record> recordClass, RecordDefinition rd, long position) throws IOException {
			Record record = createRecord(recordClass, readBasicRecord(rd, position));
			recordPositions.put(record, position);
			return Optional.of(record);
		}

		private Record createRecord(Class<? extends Record> recordClass, Record delegate) {
			if (recordClass == CentralDirectoryHeader.class) {
				return new CentralDirectoryHeader(delegate);
			} else if (recordClass == EndOfCentralDirectory.class) {
				return new EndOfCentralDirectory(delegate);
			} else if (recordClass == Zip64EndOfCentralDirectoryLocator.class) {
				return new Zip64EndOfCentralDirectoryLocator(delegate);
			} else if (recordClass == Zip64EndOfCentralDirectory.class) {
				return new Zip64EndOfCentralDirectory(delegate);
			} else if (recordClass == LocalFileHeader.class) {
				return new LocalFileHeader(delegate);
			} else {
				throw new IllegalArgumentException("Unknow record type");
			}
		}

		private Record readBasicRecord(RecordDefinition definition, long position) throws IOException {
			return new ByteBufferRecord(definition, reader.read(definition, position));
		}
		
		public long position(Record record) {
			return recordPositions.get(record).longValue();
		}
	}
	
	static final class CentralDirectoryHeader extends Record.Fowarding {

		/**
		 * Directory entry signature (aka "central file header signature", section
		 * 4.3.12 APPNOTE.TXT)
		 */
		private static final long SIGNATURE = 0x02014b50L;
		
		public static final Field CFHS = createLEField(Field.Type.UINT_32, "central file header signature");
		public static final Field VMB = createLEField(Field.Type.UINT_16, "version made by");
		public static final Field VNTE = createLEField(Field.Type.UINT_16, "version needed to extract");
		public static final Field GPBF = createLEField(Field.Type.UINT_16, "general purpose bit flag");
		public static final Field CM = createLEField(Field.Type.UINT_16, "compression method");
		public static final Field LMFT = createLEField(Field.Type.UINT_16, "last mod file time");
		public static final Field LMFD = createLEField(Field.Type.UINT_16, "last mod file date");
		public static final Field CRC32 = createLEField(Field.Type.UINT_32, "crc-32");
		public static final Field CS = createLEField(Field.Type.UINT_32, "compressed size");
		public static final Field UCS = createLEField(Field.Type.UINT_32, "uncompressed size");
		public static final Field FNL = createLEField(Field.Type.UINT_16, "file name length");
		public static final Field EFL = createLEField(Field.Type.UINT_16, "extra field length");
		public static final Field FCL = createLEField(Field.Type.UINT_16, "file comment length");
		public static final Field DNS = createLEField(Field.Type.UINT_16, "disk number start");
		public static final Field IFA = createLEField(Field.Type.UINT_16, "internal file attributes");
		public static final Field EFA = createLEField(Field.Type.UINT_32, "external file attributes");
		public static final Field ROOLH = createLEField(Field.Type.UINT_32, "relative offset of local header");
		public static final Field FN = createLEField(Field.Type.VARIABLE, "file name");
		public static final Field EF = createLEField(Field.Type.VARIABLE, "extra field");
		public static final Field FC = createLEField(Field.Type.VARIABLE, "file comment");
		
		public static final RecordDefinition DEFINITION = RecordDefinition.builder()
				.name("Central Directory Header") 
				.fields(ImmutableList.of(CFHS, VMB, VNTE, GPBF, CM, LMFT, LMFD, CRC32, CS, UCS, FNL, EFL, FCL, DNS, IFA, EFA, ROOLH, FN, EF, FC))
				.sizeDefinitionFields(ImmutableMap.of(FN, FNL, EF, EFL, FC, FCL))
				.recordClass(CentralDirectoryHeader.class)
				.signature(SIGNATURE)
				.signatureField(Optional.of(CFHS))
				.build();
		
		public CentralDirectoryHeader(Record record) {
			super(record);
		}
		
		public int versionMadeBy() {
			return delegate().uint16Value(VMB);
		}
		
		/**
		 * The upper byte indicates the compatibility of the file
        attribute information.  If the external file attributes 
        are compatible with MS-DOS and can be read by PKZIP for 
        DOS version 2.04g then this value will be zero.  If these 
        attributes are not compatible, then this value will 
        identify the host system on which the attributes are 
        compatible.  Software can use this information to determine
        the line record format for text files etc. 
		 * @return
		 */
		public Platform platform() {
			return Platform.fromValue(versionMadeBy() >> 010);
		}
		
		/**
		 * The lower byte indicates the ZIP specification version 
        (the version of this document) supported by the software 
        used to encode the file.  The value/10 indicates the major 
        version number, and the value mod 10 is the minor version 
        number.
		 * @return
		 */
		public int majorVersion() {
			return (versionMadeBy() & 0xff) / 10;
		}
		
		/**
		 * The lower byte indicates the ZIP specification version 
        (the version of this document) supported by the software 
        used to encode the file.  The value/10 indicates the major 
        version number, and the value mod 10 is the minor version 
        number.
		 * @return
		 */
		public int minorVersion() {
			return (versionMadeBy() & 0xff) % 10;
		}
		
		/**
		 * 0 - MS-DOS and OS/2 (FAT / VFAT / FAT32 file systems)
         1 - Amiga                     2 - OpenVMS
         3 - UNIX                      4 - VM/CMS
         5 - Atari ST                  6 - OS/2 H.P.F.S.
         7 - Macintosh                 8 - Z-System
         9 - CP/M                     10 - Windows NTFS
        11 - MVS (OS/390 - Z/OS)      12 - VSE
        13 - Acorn Risc               14 - VFAT
        15 - alternate MVS            16 - BeOS
        17 - Tandem                   18 - OS/400
        19 - OS X (Darwin)            20 thru 255 - unused
		 * @author mbarbero
		 *
		 */
		enum Platform {
			MSDOS_OS2(0), AMIGA(1), OPEN_VMS(2),
			UNIX(3), VM_CMS(4), ATARI_ST(5),
			OS2_HPFS(6), MACINTOSH(7), Z_SYSTEM(8),
			CP_M(9), WINDOWS_NTFS(10), MVS(11),
			VSE(12), ACORN_RISC(13), VFAT(14),
			ALTERNATE_MVS(15), BEOS(16), TANDEM(17),
			OS_400(18), OSX(19),UNKNOWN(-1);
			
			private final int value;

			private Platform(int value) {
				this.value = value;
			}
			
			public static Platform fromValue(int value) {
				for (Platform p : Platform.values()) {
					if (p.value == value) {
						return p;
					}
				}
				return Platform.UNKNOWN;
			}
		}
		
		public UnsignedInteger externalFileAttributes() {
			return delegate().uint32Value(EFA);
		}
		
		public Set<PosixFilePermission> posixPermissions() {
			if (platform() == Platform.UNIX) {
				return MorePosixFilePermissions.fromFileMode(((externalFileAttributes().longValue() >> 16) & 0x1FF));
			} else {
				return ImmutableSet.of();
			}
		}
		
		public String filename() {
			return delegate().stringValue(FN, StandardCharsets.UTF_8);
		}
	}
	
	/**
	 * end of central dir signature    4 bytes  (0x06054b50)
      number of this disk             2 bytes
      number of the disk with the
      start of the central directory  2 bytes
      total number of entries in the
      central directory on this disk  2 bytes
      total number of entries in
      the central directory           2 bytes
      size of the central directory   4 bytes
      offset of start of central
      directory with respect to
      the starting disk number        4 bytes
      .ZIP file comment length        2 bytes
      .ZIP file comment       (variable size)
	 *
	 */
	static final class EndOfCentralDirectory extends Record.Fowarding {
		public static final long SIGNATURE = 0x06054b50L;
		
		public static final Field EOCDLS = createLEField(Field.Type.UINT_32, "end of central dir signature");
		public static final Field NOTD = createLEField(Field.Type.UINT_16, "number of this disk");
		public static final Field NOTDWTSITCD = createLEField(Field.Type.UINT_16, "number of the disk with the start of the central directory");
		public static final Field TNOEITCDOTD = createLEField(Field.Type.UINT_16, "total number of entries in the central directory on this disk");
		public static final Field TNOEITCD = createLEField(Field.Type.UINT_16, "total number of entries in the central directory");
		public static final Field SOTCD = createLEField(Field.Type.UINT_32, "size of the central directory");
		public static final Field OOSOCDWRTTSDN = createLEField(Field.Type.UINT_32, "offset of start of central directory with respect to the starting disk number");
		public static final Field ZFCL = createLEField(Field.Type.UINT_16, "ZIP file comment length");
		public static final Field ZFC = createLEField(Field.Type.VARIABLE, "ZIP file comment");
		
		public static final RecordDefinition DEFINITION = RecordDefinition.builder()
				.name("End of Central Directory") 
				.fields(ImmutableList.of(EOCDLS, NOTD, NOTDWTSITCD, TNOEITCDOTD, TNOEITCD, SOTCD, OOSOCDWRTTSDN, ZFCL, ZFC))
				.sizeDefinitionFields(ImmutableMap.of(ZFC, ZFCL))
				.recordClass(EndOfCentralDirectory.class)
				.signature(SIGNATURE)
				.signatureField(Optional.of(EOCDLS))
				.build();
		
		public static final int MIN_SIZE = computeMinSize();
		public static final int MAX_SIZE = MIN_SIZE + Field.UINT16_MAX_VALUE; // for ZIP file comment
		
		private static int computeMinSize() {
			int minSize = 0;
			for (Field f : DEFINITION.fields()) {
				if (f.type() != Field.Type.VARIABLE) {
					minSize += f.type().size();
				}
			}
			return minSize;
		}
		
		public EndOfCentralDirectory(Record delegate) {
			super(delegate);
		}
		
		public UnsignedInteger offsetOfStartOfCentralDirectoryWithRespectToTheStartDiskNumber() {
			return delegate().uint32Value(OOSOCDWRTTSDN);
		}
	}
	
	/**
	 * zip64 end of central dir locator 
      signature                       4 bytes  (0x07064b50)
      number of the disk with the
      start of the zip64 end of 
      central directory               4 bytes
      relative offset of the zip64
      end of central directory record 8 bytes
      total number of disks           4 bytes
	 *
	 */
	static final class Zip64EndOfCentralDirectoryLocator extends Record.Fowarding {

		public static final long SIGNATURE = 0x07064b50L;
		
		public static final Field Z64EOCDLS = createLEField(Field.Type.UINT_32, "zip64 end of central dir locator signature");
		public static final Field NODWSZ64EOCD = createLEField(Field.Type.UINT_32, "number of the disk with the start of the zip64 end of central directory");
		public static final Field ROZ64EOCDR = createLEField(Field.Type.UINT_64, "relative offset of the zip64 end of central directory record");
		public static final Field TNOD = createLEField(Field.Type.UINT_32, "total number of disks");
		
		public static final RecordDefinition DEFINITION = RecordDefinition.builder()
				.name("Zip64 End of Central Directory Locator") 
				.fields(ImmutableList.of(Z64EOCDLS, NODWSZ64EOCD, ROZ64EOCDR, TNOD))
				.recordClass(Zip64EndOfCentralDirectoryLocator.class)
				.signature(SIGNATURE)
				.signatureField(Optional.of(Z64EOCDLS))
				.build();
		
		public Zip64EndOfCentralDirectoryLocator(Record delegate) {
			super(delegate);
		}
		
		public UnsignedInteger relativeOffsetOfTheZip64EndOfCentralDirectoryRecord() {
			return delegate().uint32Value(ROZ64EOCDR);
		}
	}
	
	/**
	 * Zip64 end of central directory record

        zip64 end of central dir 
        signature                       4 bytes  (0x06064b50)
        size of zip64 end of central
        directory record                8 bytes
        version made by                 2 bytes
        version needed to extract       2 bytes
        number of this disk             4 bytes
        number of the disk with the 
        start of the central directory  4 bytes
        total number of entries in the
        central directory on this disk  8 bytes
        total number of entries in the
        central directory               8 bytes
        size of the central directory   8 bytes
        offset of start of central
        directory with respect to
        the starting disk number        8 bytes
        zip64 extensible data sector    (variable size)
	 */
	
	static class Zip64EndOfCentralDirectory extends Record.Fowarding {

		public static final long SIGNATURE = 0x06064b50L;
		
		public static final Field Z64EOCDS = createLEField(Field.Type.UINT_32, "zip64 end of central dir signature");
		public static final Field SOZ64EOCDR = createLEField(Field.Type.UINT_64, "size of zip64 end of central directory record");
		public static final Field VMB = createLEField(Field.Type.UINT_16, "version made by");
		public static final Field VNTE = createLEField(Field.Type.UINT_16, "version needed to extract");
		public static final Field NOTD = createLEField(Field.Type.UINT_32, "number of this disk");
		public static final Field NOTDWTSOTCD = createLEField(Field.Type.UINT_32, "number of the disk with the start of the central directory");
		public static final Field TNOEITCDOTD = createLEField(Field.Type.UINT_64, "total number of entries in the central directory on this disk");
		public static final Field TNOEITCD = createLEField(Field.Type.UINT_64, "total number of entries in the central directory");
		public static final Field SOTCD = createLEField(Field.Type.UINT_64, "size of the central directory");
		public static final Field OOSOCDWRTTSDN = createLEField(Field.Type.UINT_64, "offset of start of central directory with respect to the starting disk number");
		
		public static final RecordDefinition DEFINITION = RecordDefinition.builder()
				.name("Zip64 End of Central Directory") 
				.fields(ImmutableList.of(Z64EOCDS, SOZ64EOCDR, VMB, VNTE, NOTD, NOTDWTSOTCD, TNOEITCDOTD, TNOEITCD, SOTCD, OOSOCDWRTTSDN))
				.recordClass(Zip64EndOfCentralDirectory.class)
				.signature(SIGNATURE)
				.signatureField(Optional.of(Z64EOCDS))
				.build();
		
		public Zip64EndOfCentralDirectory(Record delegate) {
			super(delegate);
		}
		
		public long offsetOfStartOfCentralDirectoryWithRespectToTheStartingDiskNumber() {
			UnsignedLong v = delegate().uint64Value(OOSOCDWRTTSDN);
			if (v.compareTo(UnsignedLong.valueOf(Long.MAX_VALUE)) > 0) {
				throw new IllegalStateException("Can not handle uint64 offset larger than Long.MAX_VALUE");
			}
			return v.longValue();
		}
	}
	
	/**
	 * Local file header:

      local file header signature     4 bytes  (0x04034b50)
      version needed to extract       2 bytes
      general purpose bit flag        2 bytes
      compression method              2 bytes
      last mod file time              2 bytes
      last mod file date              2 bytes
      crc-32                          4 bytes
      compressed size                 4 bytes
      uncompressed size               4 bytes
      file name length                2 bytes
      extra field length              2 bytes

      file name (variable size)
      extra field (variable size)
	 */
	static class LocalFileHeader extends Record.Fowarding {

		private static final long SIGNATURE = 0x04034b50L;
		public static final Field LFHS = createLEField(Field.Type.UINT_32, "local file header signature");
		public static final Field VNTE = createLEField(Field.Type.UINT_16, "version needed to extract");
		/* We don't need the rest of the fields in this version */
		
		public static final RecordDefinition DEFINITION = RecordDefinition.builder()
				.name("Local file header")
				.fields(ImmutableList.of(LFHS, VNTE))
				.recordClass(LocalFileHeader.class)
				.signature(SIGNATURE)
				.signatureField(Optional.of(LFHS))
				.build();
		
		public LocalFileHeader(Record delegate) {
			super(delegate);
		}
		
	}
}
