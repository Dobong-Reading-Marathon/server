package com.dobong.dobong.participant;

import com.dobong.dobong.config.TimeConfig;
import com.dobong.dobong.progress.CompletionStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 참가자 명단 엑셀(xlsx) 파일을 만든다 (F-0804). DB에 접근하지 않는다.
 *
 * <ul>
 *   <li>글자 열은 글자 셀에 글자 서식(@)으로 넣는다. 전화번호 앞자리 0이 지워지지 않고,
 *       "="로 시작하는 이름도 수식으로 실행되지 않는다 (셀을 고쳐 다시 입력해도 글자로 남는다)</li>
 *   <li>숫자 열은 숫자 셀로 넣어 엑셀에서 정렬하고 합계를 낼 수 있게 한다</li>
 *   <li>열 너비는 글자 수로 계산한다. POI의 autoSizeColumn은 서버에 글꼴이 있어야 해서 쓰지 않는다</li>
 * </ul>
 */
public final class ParticipantExcel {

	static final String SHEET_NAME = "참가자";

	private static final int MAX_COLUMN_WIDTH = 40; // 글자 수. 서평처럼 긴 값은 없지만 너무 넓어지지 않게

	/** 열 하나. value가 Number면 숫자 셀, String이면 글자 셀, null이면 빈칸 */
	private record Column(String header, Function<Participant, Object> value) {
	}

	private static final List<Column> COLUMNS = List.of(
			new Column("참가 신청 번호", p -> p.row().target().enrollmentId()),
			new Column("이름", p -> p.row().name()),
			new Column("전화번호", p -> formatPhone(p.row().phone())),
			new Column("대출회원번호", p -> p.row().loanMemberNo()),
			new Column("코스", p -> p.row().target().courseName()),
			new Column("완주 상태", p -> label(p.completion().status())),
			new Column("누적 쪽수", p -> p.completion().pages().current()),
			new Column("완주 쪽수", p -> p.completion().pages().target()),
			new Column("쪽수 진행률(%)", p -> p.completion().pages().percent()),
			new Column("서평 편수", p -> p.completion().reviews().current()),
			new Column("완주 서평 편수", p -> p.completion().reviews().target()),
			new Column("소장도서 권수", p -> p.completion().books().current()),
			new Column("완독 권수", p -> p.progress().completedBooks()),
			new Column("km", p -> p.progress().readKm()),
			new Column("인증서 번호", p -> p.row().certificateNo()),
			new Column("완주 승인일", p -> date(p.row().approvedAt())),
			new Column("신청일", p -> date(p.row().enrolledAt())));

	private ParticipantExcel() {
	}

	/** 첫 줄은 머리글(고정, 필터), 그 아래는 참가자 한 명씩. 참가자가 없으면 머리글만 */
	public static byte[] write(List<Participant> participants) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet(SHEET_NAME);

			CellStyle text = workbook.createCellStyle();
			text.setDataFormat(workbook.createDataFormat().getFormat("@"));
			CellStyle header = workbook.createCellStyle();
			header.cloneStyleFrom(text);
			Font bold = workbook.createFont();
			bold.setBold(true);
			header.setFont(bold);

			int[] widths = new int[COLUMNS.size()];
			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < COLUMNS.size(); i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(COLUMNS.get(i).header());
				cell.setCellStyle(header);
				widths[i] = displayWidth(COLUMNS.get(i).header());
			}

			for (int r = 0; r < participants.size(); r++) {
				Row row = sheet.createRow(r + 1);
				for (int i = 0; i < COLUMNS.size(); i++) {
					Object value = COLUMNS.get(i).value().apply(participants.get(r));
					Cell cell = row.createCell(i);
					if (value instanceof Number number) {
						cell.setCellValue(number.doubleValue());
					} else {
						cell.setCellStyle(text);
						if (value != null) {
							cell.setCellValue((String) value);
						}
					}
					widths[i] = Math.max(widths[i], value == null ? 0 : displayWidth(value.toString()));
				}
			}

			for (int i = 0; i < COLUMNS.size(); i++) {
				sheet.setColumnWidth(i, Math.min(MAX_COLUMN_WIDTH, widths[i] + 2) * 256);
			}
			sheet.createFreezePane(0, 1);
			sheet.setAutoFilter(new CellRangeAddress(0, participants.size(), 0, COLUMNS.size() - 1));

			workbook.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// 01012345678 → 010-1234-5678, 0101234567 → 010-123-4567. 형식이 다르면 그대로
	static String formatPhone(String digits) {
		if (digits == null || !digits.matches("\\d{10,11}")) {
			return digits;
		}
		int middleEnd = digits.length() - 4;
		return digits.substring(0, 3) + "-" + digits.substring(3, middleEnd) + "-" + digits.substring(middleEnd);
	}

	private static String label(CompletionStatus status) {
		return switch (status) {
			case IN_PROGRESS -> "진행 중";
			case PENDING_APPROVAL -> "완주 승인 대기";
			case COMPLETED -> "완주";
		};
	}

	// 한국 날짜 (yyyy-MM-dd). 서버가 UTC여도 밤 9시 이후 신청이 다음 날로 찍히지 않게
	private static String date(Instant instant) {
		return instant == null ? null : LocalDate.ofInstant(instant, TimeConfig.SEOUL).toString();
	}

	// 엑셀 열 너비 단위로 대략 계산. 한글 같은 넓은 글자는 2칸
	private static int displayWidth(String value) {
		return value.codePoints().map(c -> c < 0x1100 ? 1 : 2).sum();
	}
}
