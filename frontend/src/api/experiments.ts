import { http } from "./http";
import type {
  DataResponse,
  Experiment,
  ExperimentFilters,
  ExperimentStatus,
  ExperimentWriteInput,
  PageResponse,
} from "@/types/api";

export const experimentApi = {
  /** 上传电子实验记录的真实文件附件。 */
  async uploadAttachment(
    experimentNo: string,
    file: File,
    kind: "process_image" | "result_file",
  ): Promise<Experiment> {
    const body = new FormData();
    body.append("file", file);
    body.append("kind", kind);
    const response = await http.post<DataResponse<Experiment>>(
      `/experiments/${experimentNo}/attachments`,
      body,
    );
    return response.data.data;
  },

  /** 删除真实附件并返回删除后的最新实验记录。 */
  async deleteAttachment(
    experimentNo: string,
    attachmentId: string,
  ): Promise<Experiment> {
    const response = await http.delete<DataResponse<Experiment>>(
      `/experiments/${experimentNo}/attachments/${attachmentId}`,
    );
    return response.data.data;
  },

  /**
   * 查询当前用户可见实验
   *
   * @param filters 实验状态、项目和关键字筛选
   * @returns 实验分页数据
   */
  async list(filters: ExperimentFilters = {}): Promise<PageResponse<Experiment>> {
    const response = await http.get<PageResponse<Experiment>>("/experiments", {
      params: filters,
    });
    return response.data;
  },

  /**
   * 创建实验计划和电子实验记录
   *
   * @param payload 实验计划及记录字段
   * @returns 新建实验
   */
  async create(payload: ExperimentWriteInput): Promise<Experiment> {
    const response = await http.post<DataResponse<Experiment>>(
      "/experiments",
      payload,
    );
    return response.data.data;
  },

  /**
   * 更新实验计划和电子实验记录
   *
   * @param experimentNo 实验编号
   * @param version 当前资源版本
   * @param payload 待更新字段
   * @returns 更新后的实验
   */
  async update(
    experimentNo: string,
    version: number,
    payload: ExperimentWriteInput,
  ): Promise<Experiment> {
    const response = await http.patch<DataResponse<Experiment>>(
      `/experiments/${experimentNo}`,
      payload,
      { headers: { "If-Match": `"${version}"` } },
    );
    return response.data.data;
  },

  /**
   * 复制实验计划
   *
   * @param experimentNo 源实验编号
   * @returns 新建实验副本
   */
  async copy(experimentNo: string): Promise<Experiment> {
    const response = await http.post<DataResponse<Experiment>>(
      `/experiments/${experimentNo}/copy`,
      {},
    );
    return response.data.data;
  },

  /**
   * 执行实验状态迁移
   *
   * @param experimentNo 实验编号
   * @param version 当前资源版本
   * @param targetStatus 目标状态
   * @returns 状态迁移后的实验
   */
  async transition(
    experimentNo: string,
    version: number,
    targetStatus: ExperimentStatus,
  ): Promise<Experiment> {
    const response = await http.post<DataResponse<Experiment>>(
      `/experiments/${experimentNo}/transition`,
      { target_status: targetStatus },
      { headers: { "If-Match": `"${version}"` } },
    );
    return response.data.data;
  },
};
