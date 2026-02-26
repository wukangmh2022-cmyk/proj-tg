use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub struct SimpleDate {
    pub year: i32,
    pub month: u32,
    pub day: u32,
}

impl SimpleDate {
    pub fn from_ymd(year: i32, month: u32, day: u32) -> Option<Self> {
        if !(1..=12).contains(&month) {
            return None;
        }

        let max_day = days_in_month(year, month);
        if day == 0 || day > max_day {
            return None;
        }

        Some(Self { year, month, day })
    }

    pub fn to_yyyy_mm_dd(self) -> String {
        format!("{:04}-{:02}-{:02}", self.year, self.month, self.day)
    }

    pub fn days_since_unix_epoch(self) -> i64 {
        days_from_civil(self.year, self.month, self.day)
    }

    pub fn add_days(self, days: i64) -> Self {
        let (year, month, day) = civil_from_days(self.days_since_unix_epoch() + days);
        Self { year, month, day }
    }
}

pub fn now_utc_date() -> Result<SimpleDate, String> {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| format!("system clock error: {e}"))?;
    let days = (now.as_secs() / 86_400) as i64;
    let (year, month, day) = civil_from_days(days);
    Ok(SimpleDate { year, month, day })
}

pub fn extract_first_date(input: &str) -> Option<SimpleDate> {
    let bytes = input.as_bytes();
    if bytes.len() < 10 {
        return None;
    }

    for idx in 0..=(bytes.len() - 10) {
        let y = match parse_uint(bytes, idx, 4) {
            Some(v) => v as i32,
            None => continue,
        };
        let sep1 = bytes[idx + 4] as char;
        if sep1 != '-' && sep1 != '/' {
            continue;
        }

        let m = match parse_uint(bytes, idx + 5, 2) {
            Some(v) => v,
            None => continue,
        };
        let sep2 = bytes[idx + 7] as char;
        if sep2 != '-' && sep2 != '/' {
            continue;
        }

        let d = match parse_uint(bytes, idx + 8, 2) {
            Some(v) => v,
            None => continue,
        };
        if let Some(date) = SimpleDate::from_ymd(y, m, d) {
            return Some(date);
        }
    }

    None
}

fn parse_uint(bytes: &[u8], start: usize, len: usize) -> Option<u32> {
    if start + len > bytes.len() {
        return None;
    }

    let mut out: u32 = 0;
    for ch in bytes.iter().skip(start).take(len) {
        if !ch.is_ascii_digit() {
            return None;
        }
        out = out.checked_mul(10)?.checked_add((ch - b'0') as u32)?;
    }
    Some(out)
}

fn days_in_month(year: i32, month: u32) -> u32 {
    match month {
        1 | 3 | 5 | 7 | 8 | 10 | 12 => 31,
        4 | 6 | 9 | 11 => 30,
        2 if is_leap_year(year) => 29,
        2 => 28,
        _ => 0,
    }
}

fn is_leap_year(year: i32) -> bool {
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

fn days_from_civil(year: i32, month: u32, day: u32) -> i64 {
    let adjusted_year = year - if month <= 2 { 1 } else { 0 };
    let era = if adjusted_year >= 0 {
        adjusted_year / 400
    } else {
        (adjusted_year - 399) / 400
    };
    let yoe = adjusted_year - era * 400;
    let month_prime = month as i32 + if month > 2 { -3 } else { 9 };
    let doy = (153 * month_prime + 2) / 5 + day as i32 - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    (era as i64) * 146_097 + (doe as i64) - 719_468
}

fn civil_from_days(days_since_epoch: i64) -> (i32, u32, u32) {
    let shifted = days_since_epoch + 719_468;
    let era = if shifted >= 0 {
        shifted / 146_097
    } else {
        (shifted - 146_096) / 146_097
    };

    let doe = (shifted - era * 146_097) as i32;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let year = yoe + era as i32 * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let month_prime = (5 * doy + 2) / 153;
    let day = doy - (153 * month_prime + 2) / 5 + 1;
    let month = month_prime + if month_prime < 10 { 3 } else { -9 };
    let year = year + if month <= 2 { 1 } else { 0 };

    (year, month as u32, day as u32)
}

#[cfg(test)]
mod tests {
    use super::{extract_first_date, SimpleDate};

    #[test]
    fn parses_iso_date() {
        let parsed = extract_first_date("msg date=2025-11-03 text=buy").expect("date");
        assert_eq!(parsed.to_yyyy_mm_dd(), "2025-11-03");
    }

    #[test]
    fn date_round_trip() {
        let d = SimpleDate::from_ymd(2026, 2, 26).expect("valid date");
        let after = d.add_days(-730).add_days(730);
        assert_eq!(after.to_yyyy_mm_dd(), d.to_yyyy_mm_dd());
    }
}
