package com.dobong.dobong.participant;

import static org.assertj.core.api.Assertions.assertThat;

import com.dobong.dobong.progress.EnrollmentTarget;
import com.dobong.dobong.progress.ReadingProgress;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 만든 xlsx 파일을 다시 열어서 셀 종류와 값을 확인한다. DB 없이 돈다.
 */
class ParticipantExcelTest {

	// 열 번호 (ParticipantExcel.COLUMNS 순서)
	private static final int ID = 0;
	private static final int NAME = 1;
	private static final int PHONE = 2;
	private static final int LOAN_NO = 3;
	private static final int COURSE = 4;
	private static final int STATUS = 5;
	private static final int READ_PAGES = 6;
	private static final int TARGET_PAGES = 7;
	private static final int PAGE_PERCENT = 8;
	private static final int REVIEWS = 9;
	private static final int KM = 13;
	private static final int CERTIFICATE = 14;
	private static final int APPROVED_AT = 15;
	private static final int VERIFIED_LOANS = 16;
	private static final int APPROVAL_NOTE = 17;
	private static final int ENROLLED_AT = 18;

	@Test
	@DisplayName("머리글을 고정하고, 참가자를 한 줄씩 넣는다")
	void headerAndRows() throws Exception {
		Sheet sheet = read(ParticipantExcel.write(List.of(participant("홍길동", "01012345678", false, 12450, 7))));

		assertThat(sheet.getSheetName()).isEqualTo(ParticipantExcel.SHEET_NAME);
		assertThat(sheet.getPaneInformation().getHorizontalSplitTopRow()).isEqualTo((short) 1);
		assertThat(sheet.getLastRowNum()).isEqualTo(1);

		Row header = sheet.getRow(0);
		assertThat(header.getCell(NAME).getStringCellValue()).isEqualTo("이름");
		assertThat(header.getCell(PHONE).getStringCellValue()).isEqualTo("전화번호");

		Row row = sheet.getRow(1);
		assertThat(row.getCell(ID).getNumericCellValue()).isEqualTo(15);
		assertThat(row.getCell(NAME).getStringCellValue()).isEqualTo("홍길동");
		assertThat(row.getCell(LOAN_NO).getStringCellValue()).isEqualTo("LM-0001");
		assertThat(row.getCell(COURSE).getStringCellValue()).isEqualTo("하프코스");
		assertThat(row.getCell(STATUS).getStringCellValue()).isEqualTo("진행 중");
		assertThat(row.getCell(TARGET_PAGES).getNumericCellValue()).isEqualTo(17500);
		assertThat(row.getCell(PAGE_PERCENT).getNumericCellValue()).isEqualTo(71);
		assertThat(row.getCell(REVIEWS).getNumericCellValue()).isEqualTo(7);
		assertThat(row.getCell(KM).getNumericCellValue()).isEqualTo(12.45);
	}

	@Test
	@DisplayName("전화번호는 앞자리 0이 남는 글자 셀, 숫자 열은 숫자 셀")
	void cellTypes() throws Exception {
		Row row = read(ParticipantExcel.write(List.of(participant("홍길동", "01012345678", false, 12450, 7)))).getRow(1);

		Cell phone = row.getCell(PHONE);
		assertThat(phone.getCellType()).isEqualTo(CellType.STRING);
		assertThat(phone.getStringCellValue()).isEqualTo("010-1234-5678");
		assertThat(phone.getCellStyle().getDataFormatString()).isEqualTo("@");

		assertThat(row.getCell(READ_PAGES).getCellType()).isEqualTo(CellType.NUMERIC);
		assertThat(row.getCell(READ_PAGES).getNumericCellValue()).isEqualTo(12450);
	}

	@Test
	@DisplayName("=로 시작하는 이름도 수식이 아니라 글자로 들어간다")
	void formulaLikeValueStaysText() throws Exception {
		Cell name = read(ParticipantExcel.write(List.of(participant("=1+1", "01012345678", false, 0, 0)))).getRow(1).getCell(NAME);

		assertThat(name.getCellType()).isEqualTo(CellType.STRING);
		assertThat(name.getStringCellValue()).isEqualTo("=1+1");
	}

	@Test
	@DisplayName("날짜는 한국 날짜로 쓰고, 없는 값은 빈칸")
	void datesAndBlanks() throws Exception {
		Row inProgress = read(ParticipantExcel.write(List.of(participant("홍길동", "01012345678", false, 0, 0)))).getRow(1);
		// 2027-02-01T15:30Z = 한국 2027-02-02 00:30
		assertThat(inProgress.getCell(ENROLLED_AT).getStringCellValue()).isEqualTo("2027-02-02");
		assertThat(inProgress.getCell(CERTIFICATE).getCellType()).isEqualTo(CellType.BLANK);
		assertThat(inProgress.getCell(APPROVED_AT).getCellType()).isEqualTo(CellType.BLANK);
		assertThat(inProgress.getCell(VERIFIED_LOANS).getCellType()).isEqualTo(CellType.BLANK);

		Row completed = read(ParticipantExcel.write(List.of(participant("홍길동", "01012345678", true, 0, 0)))).getRow(1);
		assertThat(completed.getCell(STATUS).getStringCellValue()).isEqualTo("완주");
		assertThat(completed.getCell(CERTIFICATE).getStringCellValue()).isEqualTo("DB-2027-0001");
		assertThat(completed.getCell(APPROVED_AT).getStringCellValue()).isEqualTo("2027-11-30");
		assertThat(completed.getCell(VERIFIED_LOANS).getNumericCellValue()).isEqualTo(52);
		assertThat(completed.getCell(APPROVAL_NOTE).getStringCellValue()).isEqualTo("오프라인 수첩 합산");
	}

	@Test
	@DisplayName("참가자가 없으면 머리글만 있다")
	void empty() throws Exception {
		Sheet sheet = read(ParticipantExcel.write(List.of()));

		assertThat(sheet.getLastRowNum()).isZero();
		assertThat(sheet.getRow(0).getCell(NAME).getStringCellValue()).isEqualTo("이름");
	}

	@Test
	@DisplayName("전화번호는 하이픈을 넣고, 형식이 다르면 그대로 둔다")
	void formatPhone() {
		assertThat(ParticipantExcel.formatPhone("01012345678")).isEqualTo("010-1234-5678");
		assertThat(ParticipantExcel.formatPhone("0111234567")).isEqualTo("011-123-4567");
		assertThat(ParticipantExcel.formatPhone("12345")).isEqualTo("12345");
		assertThat(ParticipantExcel.formatPhone(null)).isNull();
	}

	/** 하프코스(17,500쪽, 서평 10편, 대출 50권) 참가자 */
	private static Participant participant(String name, String phone, boolean completed, int readPages, int reviews) {
		EnrollmentTarget target = new EnrollmentTarget(15, 2, 2027, 1, 7, "하프코스", false, 17500, 50, 10, completed, 21);
		ParticipantRow row = new ParticipantRow(target, 10, name, phone, "LM-0001",
				completed ? "DB-2027-0001" : null,
				completed ? Instant.parse("2027-11-30T01:00:00Z") : null,
				completed ? 52 : null,
				completed ? "오프라인 수첩 합산" : null,
				Instant.parse("2027-02-01T15:30:00Z"));
		return Participant.of(row, new ReadingProgress(15, readPages, 3, reviews));
	}

	// 메모리에서 연 파일이라 닫지 않아도 된다. 닫으면 시트를 읽을 수 없다
	private static Sheet read(byte[] xlsx) throws Exception {
		return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheetAt(0);
	}
}
