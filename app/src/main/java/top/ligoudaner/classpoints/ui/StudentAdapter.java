package top.ligoudaner.classpoints.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.ligoudaner.classpoints.databinding.ItemStudentBinding;
import top.ligoudaner.classpoints.model.Student;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> {

    private List<Student> students = new ArrayList<>();
    private Set<Integer> selectedStudentIds = new HashSet<>();
    private OnStudentClickListener listener;

    public interface OnStudentClickListener {
        void onAddPointClick(Student student);
        void onStudentLongClick(Student student);
        void onStudentDoubleClick(Student student);
        void onSelectionChanged(int count);
    }

    public void setOnStudentClickListener(OnStudentClickListener listener) {
        this.listener = listener;
    }

    public void setStudents(List<Student> students) {
        this.students = students;
        notifyDataSetChanged();
    }

    public List<Integer> getSelectedIds() {
        return new ArrayList<>(selectedStudentIds);
    }

    public void clearSelection() {
        selectedStudentIds.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStudentBinding binding = ItemStudentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Student student = students.get(position);
        holder.binding.tvStudentId.setText(String.valueOf(student.id));
        holder.binding.tvStudentName.setText(student.name);

        // 多选逻辑
        holder.binding.cbSelect.setOnCheckedChangeListener(null);
        holder.binding.cbSelect.setChecked(selectedStudentIds.contains(student.id));
        holder.binding.cbSelect.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                selectedStudentIds.add(student.id);
            } else {
                selectedStudentIds.remove(student.id);
            }
            if (listener != null) listener.onSelectionChanged(selectedStudentIds.size());
        });

        double weeklyTotal = student.currentWeeklyPoints;
        double cumulativeTotal = student.totalPoints + student.currentWeeklyPoints;

        holder.binding.tvWeeklyTotal.setText("本周总分: " + weeklyTotal);
        holder.binding.tvCumulativeTotal.setText("累积总分: " + cumulativeTotal);

        holder.binding.btnAddPoint.setOnClickListener(v -> {
            if (listener != null) listener.onAddPointClick(student);
        });

        holder.itemView.setOnClickListener(new android.view.View.OnClickListener() {
            private long lastClickTime = 0;
            @Override
            public void onClick(android.view.View v) {
                long clickTime = System.currentTimeMillis();
                if (clickTime - lastClickTime < 300) { // Double click interval
                    if (listener != null) listener.onStudentDoubleClick(student);
                }
                lastClickTime = clickTime;
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onStudentLongClick(student);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemStudentBinding binding;
        ViewHolder(ItemStudentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
