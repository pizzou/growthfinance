import { get } from './api';
import { DashboardStats, ChartPoint } from '../types/index';

export const getDashboardStats  = (): Promise<DashboardStats> => get('/dashboard/stats') as Promise<DashboardStats>;
export const getLoanChartData   = (): Promise<ChartPoint[]>   => get('/dashboard/charts/loans') as Promise<ChartPoint[]>;
export const getCollectionChart = (): Promise<ChartPoint[]>   => get('/dashboard/charts/collections') as Promise<ChartPoint[]>;