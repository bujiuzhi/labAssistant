function addDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

function addOneMonth(date: Date): Date {
  const result = new Date(date);
  const originalDay = result.getDate();
  result.setDate(1);
  result.setMonth(result.getMonth() + 1);
  const lastDayOfTargetMonth = new Date(
    result.getFullYear(),
    result.getMonth() + 1,
    0,
  ).getDate();
  result.setDate(Math.min(originalDay, lastDayOfTargetMonth));
  return result;
}

/** Element Plus 日期选择器共用的未来日期快捷项 */
export const futureDateShortcuts = [
  {
    text: "一周后",
    value: () => addDays(new Date(), 7),
  },
  {
    text: "一个月后",
    value: () => addOneMonth(new Date()),
  },
];
