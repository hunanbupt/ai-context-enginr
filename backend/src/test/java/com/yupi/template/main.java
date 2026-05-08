package com.yupi.template;

import java.util.Scanner;



public class main {
    static class ListNode {
        int val;
        ListNode next;
        ListNode() {}
        ListNode(int val) { this.val = val; }
        ListNode(int val, ListNode next) { this.val = val; this.next = next; }
    }
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // 读取链表长度和k值（如果题目给了长度），也可以直接读取一串数字
        int n = sc.nextInt();
        int k = sc.nextInt();

        // 构建链表
        ListNode dummy = new ListNode(0);
        ListNode curr = dummy;
        for (int i = 0; i < n; i++) {
            int val = sc.nextInt();
            curr.next = new ListNode(val);
            curr = curr.next;
        }

        // 调用核心反转函数
        ListNode newHead = reverseKGroup(dummy.next, k);

        // 输出结果
        while (newHead != null) {
            System.out.print(newHead.val);
            if (newHead.next != null) {
                System.out.print("->");
            }
            newHead = newHead.next;
        }
        System.out.print("->NULL");
        sc.close();
    }

    // 核心逻辑（和之前的力扣版一致，无注释）
    public static ListNode reverseKGroup(ListNode head, int k) {
        ListNode dummy = new ListNode(0);
        dummy.next = head;
        ListNode preTail = dummy;

        while (true) {
            ListNode check = preTail.next;
            int count = 0;
            while (check != null && count < k) {
                check = check.next;
                count++;
            }
            if (count < k) break;

            ListNode curHead = preTail.next;
            ListNode curTail = curHead;
            for (int i = 1; i < k; i++) {
                curTail = curTail.next;
            }
            ListNode nextHead = curTail.next;

            ListNode prev = null;
            ListNode curr = curHead;
            while (curr != nextHead) {
                ListNode nextTemp = curr.next;
                curr.next = prev;
                prev = curr;
                curr = nextTemp;
            }

            preTail.next = prev;
            curHead.next = nextHead;
            preTail = curHead;
        }
        return dummy.next;
    }
}