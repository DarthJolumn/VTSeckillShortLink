// 秒杀服务接口（/seckill） · 对应后端 2.8 接口文档 §二
import http from '@/infra/request'

export const seckillApi = {
  createActivity(data) { return http.post('/seckill/activity', data) },
  updateActivityStatus(id, status) { return http.put(`/seckill/activity/${id}/status`, { status }) },
  activityDetail(id) { return http.get(`/seckill/activity/${id}`) },
  activityList(params) { return http.get('/seckill/activity/list', { params }) },
  /** 抢购下单（阻塞等待 Kafka 结果）· 单独 15s 超时 */
  placeOrder(activityId) {
    return http.post('/seckill/order', { activityId }, { timeout: 15000 })
  },
  orderList(params) { return http.get('/seckill/order/list', { params }) },
  orderDetail(orderNo) { return http.get(`/seckill/order/${orderNo}`) },
  cancelOrder(orderNo) { return http.put(`/seckill/order/${orderNo}/cancel`) },
  refundOrder(orderNo) { return http.put(`/seckill/order/${orderNo}/refund`) },
}
